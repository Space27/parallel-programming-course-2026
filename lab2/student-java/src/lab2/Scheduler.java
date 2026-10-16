package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /**
     * Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner.
     */
    boolean logging;
    /**
     * Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100".
     */
    final List<String> log = new ArrayList<>();
    /**
     * null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2).
     */
    String deadlock;

    private final List<ThreadInfo> threads = new ArrayList<>();
    private final Semaphore schedSemaphore = new Semaphore(0);
    private static final ThreadLocal<ThreadInfo> local = new ThreadLocal<>();
    private final StringBuilder trace = new StringBuilder();

    private final List<SchedLock> locks = new ArrayList<>();
    private boolean aborted;

    public Scheduler() {
    }

    /**
     * Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn.
     */
    public void spawn(Runnable body) {
        int id = threads.size();
        Semaphore s = new Semaphore(0);

        Thread t = new Thread(() -> {
            ThreadInfo me = threads.get(id);
            local.set(me);
            try {
                body.run();
            } catch (AbortedError _) {
            } finally {
                me.status = ThreadStatus.FINISHED;
                schedSemaphore.release();
            }
        });

        threads.add(new ThreadInfo(id, t, s, this));
    }

    /**
     * Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last).
     */
    public void run(Strategy strategy) {
        for (ThreadInfo thread : threads) {
            thread.thread.start();
            schedSemaphore.acquireUninterruptibly();
        }
        int last = -1;
        try {
            while (true) {
                List<ThreadInfo> enabled = threads.stream().filter(this::isEnabled).toList();
                if (enabled.isEmpty()) {
                    checkDeadlock();
                    break;
                }
                int id = strategy.pick(enabled.stream().map(t -> t.id).toList(), last);
                if (id < 0 || id >= threads.size() || !isEnabled(threads.get(id))) {
                    throw new RuntimeException("Invalid thread id=" + id + " from strategy");
                }
                last = id;
                trace.append((char) ('A' + id));
                ThreadInfo currentThread = threads.get(id);
                currentThread.status = ThreadStatus.RUNNING;
                currentThread.semaphore.release();
                schedSemaphore.acquireUninterruptibly();
            }
        } finally {
            abortAndJoin();
        }
    }

    private void abortAndJoin() {
        aborted = true;
        threads.forEach(t -> t.semaphore.release());

        for (ThreadInfo thread : threads) {
            try {
                thread.thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkDeadlock() {
        if (threads.stream().anyMatch(t -> t.status != ThreadStatus.FINISHED)) {
            deadlock = "held={" +
                    locks.stream().filter(lock -> lock.owner != -1)
                            .map(lock -> lock.name + ":" + (char) (lock.owner + 'A'))
                            .collect(Collectors.joining(", ")) +
                    "}";
        }
    }

    /**
     * Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB".
     */
    public String traceString() {
        return trace.toString();
    }

    /**
     * window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна.
     */
    public static void point() {
        log("window");
        touch();
    }

    /**
     * Ждать, пока cond не станет true. До этого поток не попадает в enabled.
     */
    public static void await(BooleanSupplier cond) {
        ThreadInfo me = local.get();

        if (me.touched || !cond.getAsBoolean()) {
            me.status = ThreadStatus.WAITING;
            me.waitingFor = cond;
            pass(me);

            me.waitingFor = null;
        }

        log("await done");
        touch();
    }

    private static void pass(ThreadInfo me) {
        Scheduler s = me.scheduler;
        if (s.aborted) throw new AbortedError();
        s.schedSemaphore.release();
        me.semaphore.acquireUninterruptibly();
        if (s.aborted) throw new AbortedError();
    }

    static void blockOn(SchedLock l) {
        ThreadInfo me = local.get();
        if (me.touched || l.owner != -1) {
            me.status = ThreadStatus.BLOCKED;
            me.blockedOn = l;
            pass(me);

            me.blockedOn = null;
        }
    }

    static boolean aborted() {
        return local.get().scheduler.aborted;
    }

    static int currentId() {
        return local.get().id;
    }

    public void register(SchedLock l) {
        locks.add(l);
    }

    private boolean isEnabled(ThreadInfo thread) {
        return switch (thread.status) {
            case READY -> true;
            case WAITING -> thread.waitingFor.getAsBoolean();
            case BLOCKED -> thread.blockedOn.owner == -1;
            default -> false;
        };
    }

    static void pause() {
        ThreadInfo me = local.get();
        if (me.touched) {
            me.touched = false;
            me.status = ThreadStatus.READY;
            pass(me);
        }
    }

    static void touch() {
        local.get().touched = true;
    }

    static void log(String message) {
        ThreadInfo me = local.get();
        if (me.scheduler.logging && !me.scheduler.aborted) {
            me.scheduler.log.add(String.format("%c  %s", (char) ('A' + me.id), message));
        }
    }

    enum ThreadStatus {
        RUNNING,
        READY,
        WAITING,
        BLOCKED,
        FINISHED
    }

    static class ThreadInfo {

        private final int id;
        private final Scheduler scheduler;
        private final Thread thread;
        private final Semaphore semaphore;
        private ThreadStatus status;
        private boolean touched;
        private BooleanSupplier waitingFor;
        private SchedLock blockedOn;

        ThreadInfo(
                int id, Scheduler scheduler,
                Thread thread,
                Semaphore semaphore,
                ThreadStatus status,
                boolean touched
        ) {
            this.id = id;
            this.scheduler = scheduler;
            this.thread = thread;
            this.semaphore = semaphore;
            this.status = status;
            this.touched = touched;
        }

        ThreadInfo(int id, Thread thread, Semaphore semaphore, Scheduler scheduler) {
            this(id, scheduler, thread, semaphore, ThreadStatus.READY, true);
        }
    }

    private static final class AbortedError extends Error {
        AbortedError() {
            super(null, null, false, false);
        }
    }
}
