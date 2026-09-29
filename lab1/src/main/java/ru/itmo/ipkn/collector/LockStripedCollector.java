package ru.itmo.ipkn.collector;

import ru.itmo.ipkn.MathUtils;
import ru.itmo.ipkn.Snapshot;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public class LockStripedCollector implements MetricsCollector {

    private static final int LOCK_COUNT = 16;

    private final int bucketSize;

    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong(0);
    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong sum = new AtomicLong(0);

    private final long[] buckets;
    private final ReentrantLock[] bucketLocks;


    public LockStripedCollector() {
        this(DEFAULT_BUCKET_COUNT, DEFAULT_BUCKET_SIZE);
    }

    public LockStripedCollector(int bucketCount, int bucketSize) {
        if (bucketCount < 1) {
            throw new IllegalArgumentException("Count of buckets should be more than 0");
        }
        if (bucketSize < 1) {
            throw new IllegalArgumentException("Size of buckets should be more than 0");
        }

        this.bucketSize = bucketSize;
        this.buckets = new long[bucketCount];
        this.bucketLocks = Stream.generate(ReentrantLock::new).limit(LOCK_COUNT).toArray(ReentrantLock[]::new);
    }

    @Override
    public void record(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value should be non-negative");
        }
        int bucket = (int) Math.min(value / bucketSize, buckets.length - 1);

        bucketLocks[bucket % bucketLocks.length].lock();
        try {
            buckets[bucket]++;
        } finally {
            bucketLocks[bucket % bucketLocks.length].unlock();
        }
        count.getAndIncrement();
        sum.getAndAdd(value);

        long tmp;
        do {
            tmp = min.get();
        } while (value < tmp && !min.compareAndSet(tmp, value));
        do {
            tmp = max.get();
        } while (value > tmp && !max.compareAndSet(tmp, value));
    }

    @Override
    public Snapshot snapshot() {
        if (count.get() == 0) {
            return new Snapshot(buckets.clone(), 0, 0, 0, 0, 0, 0);
        }

        long[] bucketsCopy = new long[buckets.length];
        for (int i = 0; i < bucketLocks.length; ++i) {
            bucketLocks[i].lock();
            try {
                for (int j = i; j < buckets.length; j += LOCK_COUNT) {
                    bucketsCopy[j] = buckets[j];
                }
            } finally {
                bucketLocks[i].unlock();
            }
        }

        long currentCount = count.get();
        return Snapshot.builder()
                .buckets(bucketsCopy)
                .count(currentCount)
                .sum(sum.get())
                .min(min.get())
                .max(max.get())
                .p50(MathUtils.computePercentile(bucketsCopy, bucketSize, currentCount, P50))
                .p99(MathUtils.computePercentile(bucketsCopy, bucketSize, currentCount, P99))
                .build();
    }
}
