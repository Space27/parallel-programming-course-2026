package ru.itmo.ipkn.collector;

import ru.itmo.ipkn.MathUtils;
import ru.itmo.ipkn.Snapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DoubleBufferingCollector implements MetricsCollector {

    private final int bucketSize;

    private final List<ThreadBuffers> threadBuffers = new ArrayList<>();
    private final Object snapLock = new Object();

    private final ThreadLocal<ThreadBuffers> myState;

    private volatile int active = 0;

    private long min = Long.MAX_VALUE;
    private long max = 0;
    private long count = 0;
    private long sum = 0;
    private final long[] buckets;

    public DoubleBufferingCollector() {
        this(DEFAULT_BUCKET_COUNT, DEFAULT_BUCKET_SIZE);
    }

    public DoubleBufferingCollector(int bucketCount, int bucketSize) {
        if (bucketCount < 1) {
            throw new IllegalArgumentException("Count of buckets should be more than 0");
        }
        if (bucketSize < 1) {
            throw new IllegalArgumentException("Size of buckets should be more than 0");
        }

        this.bucketSize = bucketSize;
        this.buckets = new long[bucketCount];
        this.myState = ThreadLocal.withInitial(() -> {
            ThreadBuffers s = new ThreadBuffers(bucketCount);
            synchronized (snapLock) {
                threadBuffers.add(s);
            }
            return s;
        });
    }

    @Override
    public void record(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value should be non-negative");
        }

        ThreadBuffers my = myState.get();
        int buffer;

        while (true) {
            buffer = active;
            my.inside.set(buffer);
            if (active == buffer) {
                break;
            } else {
                my.inside.setRelease(-1);
            }
        }

        int bucket = (int) Math.min(value / bucketSize, my.buckets[0].length - 1);
        my.count[buffer]++;
        my.buckets[buffer][bucket]++;
        my.sum[buffer] += value;
        my.min[buffer] = Math.min(my.min[buffer], value);
        my.max[buffer] = Math.max(my.max[buffer], value);

        my.inside.setRelease(-1);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapLock) {
            int old = active;
            active = 1 - old;
            for (ThreadBuffers s : threadBuffers) {
                while (s.inside.get() == old) {
                    Thread.onSpinWait();
                }
            }

            for (ThreadBuffers s : threadBuffers) {
                for (int i = 0; i < buckets.length; ++i) {
                    buckets[i] += s.buckets[old][i];
                }
                count += s.count[old];

                sum += s.sum[old];
                min = Math.min(min, s.min[old]);
                max = Math.max(max, s.max[old]);

                s.count[old] = 0;
                s.sum[old] = 0;
                Arrays.fill(s.buckets[old], 0);
                s.min[old] = Long.MAX_VALUE;
                s.max[old] = 0;
            }

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

    static final class ThreadBuffers {
        // Два буфера: [0] и [1].
        final long[][] buckets;
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};

        // Флаг входа: -1 = вне буферов, 0 = запись в буфер 0, 1 = запись в буфер 1
        final AtomicInteger inside = new AtomicInteger(-1);

        ThreadBuffers(int bucketCount) {
            buckets = new long[2][bucketCount];
        }
    }
}
