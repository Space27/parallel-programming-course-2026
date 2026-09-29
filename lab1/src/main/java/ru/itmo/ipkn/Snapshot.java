package ru.itmo.ipkn;

import lombok.Builder;

/**
 * Снимок содержимого коллектора в конкретный момент времени
 * @param buckets гистограмма 256 бакетов по 4 мс
 * @param count общее количество наблюдений
 * @param sum сумма всех значений времени отклика
 * @param min минимальное значение времени отклика
 * @param max максимальное значение времени отклика
 * @param p50 50-й процентиль (медиана), в миллисекундах
 * @param p99 99-й процентиль, в миллисекундах
 */
@Builder
public record Snapshot(
        long[] buckets,
        long count,
        long sum,
        long min,
        long max,
        long p50,
        long p99
) {
}
