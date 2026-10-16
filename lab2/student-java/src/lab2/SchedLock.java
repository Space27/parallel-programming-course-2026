package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {

    final String name;
    int owner = -1;

    /**
     * Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s.
     */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
        s.register(this);
    }

    /**
     * Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем.
     */
    public void lock() {
        Scheduler.blockOn(this);
        owner = Scheduler.currentId();
        Scheduler.log("lock " + name);
        Scheduler.touch();
    }

    /**
     * Пауза; освободить лок, ждавшие потоки снова могут быть выбраны.
     */
    public void unlock() {
        if (Scheduler.aborted()) return;
        Scheduler.pause();
        if (owner != Scheduler.currentId()) {
            throw new IllegalStateException(name + " unlocked by non-owner");
        }
        owner = -1;
        Scheduler.log("unlock " + name);
        Scheduler.touch();
    }
}
