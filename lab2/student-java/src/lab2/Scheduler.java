package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

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
            } finally {
                me.setStatus(ThreadStatus.FINISHED);
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
            thread.thread().start();
            schedSemaphore.acquireUninterruptibly();
        }
        int last = -1;
        List<ThreadInfo> enabled;
        while (true) {
            enabled = threads.stream().filter(this::isEnabled).toList();
            if (enabled.isEmpty()) {
                break;
            }
            int id = strategy.pick(enabled.stream().map(ThreadInfo::id).toList(), last);
            last = id;
            trace.append((char) ('A' + id));
            ThreadInfo currentThread = threads.get(id);
            currentThread.setStatus(ThreadStatus.RUNNING);
            currentThread.semaphore().release();
            schedSemaphore.acquireUninterruptibly();
        }
        for (ThreadInfo thread : threads) {
            try {
                thread.thread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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

        if (me.touched() || !cond.getAsBoolean()) {
            me.setStatus(ThreadStatus.WAITING);
            me.setWaitingFor(cond);
            me.scheduler().schedSemaphore.release();
            me.semaphore().acquireUninterruptibly();

            me.setWaitingFor(null);
        }

        log("await done");
        touch();
    }

    private boolean isEnabled(ThreadInfo thread) {
        return switch (thread.status()) {
            case READY -> true;
            case WAITING -> thread.waitingFor().getAsBoolean();
            default -> false;
        };
    }

    public static void pause() {
        ThreadInfo me = local.get();
        if (me.touched()) {
            me.setTouched(false);
            me.setStatus(ThreadStatus.READY);
            me.scheduler().schedSemaphore.release();
            me.semaphore().acquireUninterruptibly();
        }
    }

    public static void touch() {
        local.get().setTouched(true);
    }

    public static void log(String message) {
        ThreadInfo me = local.get();
        if (me.scheduler().logging) {
            me.scheduler().log.add(String.format("%c  %s", (char) ('A' + me.id), message));
        }
    }

    enum ThreadStatus {
        RUNNING,
        READY,
        WAITING,
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

        public int id() {
            return id;
        }

        public Thread thread() {
            return thread;
        }

        public ThreadStatus status() {
            return status;
        }

        public Semaphore semaphore() {
            return semaphore;
        }

        public Scheduler scheduler() {
            return scheduler;
        }

        public boolean touched() {
            return touched;
        }

        public BooleanSupplier waitingFor() {
            return waitingFor;
        }

        public void setStatus(ThreadStatus status) {
            this.status = status;
        }

        public void setTouched(boolean touched) {
            this.touched = touched;
        }

        public void setWaitingFor(BooleanSupplier waitingFor) {
            this.waitingFor = waitingFor;
        }
    }
}
