package ru.itmo.ipkn.collector;

import ru.itmo.ipkn.Snapshot;

/**
 * Интерфейс коллектора метрик
 */
public interface MetricsCollector {

    int DEFAULT_BUCKET_COUNT = 256;
    int DEFAULT_BUCKET_SIZE = 4;
    int P50 = 50;
    int P99 = 99;

    /**
     * Запись значения времени отклика
     *
     * @param value время отклика в мс
     */
    void record(long value);

    /**
     * Снимок текущего состояния коллектора
     *
     * @return {@link Snapshot} с текущими значениями
     */
    Snapshot snapshot();
}
