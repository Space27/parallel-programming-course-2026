package ru.itmo.ipkn.collector;

import ru.itmo.ipkn.MathUtils;
import ru.itmo.ipkn.Snapshot;

public class SingleThreadCollector implements MetricsCollector {

    private final int bucketSize;

    private long min = Long.MAX_VALUE;
    private long max = 0;
    private long count = 0;
    private long sum = 0;
    private final long[] buckets;

    public SingleThreadCollector() {
        this(DEFAULT_BUCKET_COUNT, DEFAULT_BUCKET_SIZE);
    }

    public SingleThreadCollector(int bucketCount, int bucketSize) {
        if (bucketCount < 1) {
            throw new IllegalArgumentException("Count of buckets should be more than 0");
        }
        if (bucketSize < 1) {
            throw new IllegalArgumentException("Size of buckets should be more than 0");
        }

        this.bucketSize = bucketSize;
        this.buckets = new long[bucketCount];
    }

    @Override
    public void record(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value should be non-negative");
        }
        int bucket = (int) Math.min(value / bucketSize, buckets.length - 1);

        buckets[bucket]++;
        count++;
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    @Override
    public Snapshot snapshot() {
        if (count == 0) {
            return new Snapshot(buckets.clone(), 0, 0, 0, 0, 0, 0);
        }

        return Snapshot.builder()
                .buckets(buckets.clone())
                .count(count)
                .sum(sum)
                .min(min)
                .max(max)
                .p50(MathUtils.computePercentile(buckets, bucketSize, count, P50))
                .p99(MathUtils.computePercentile(buckets, bucketSize, count, P99))
                .build();
    }
}
