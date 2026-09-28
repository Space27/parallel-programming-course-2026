package ru.itmo.ipkn.collector;

import ru.itmo.ipkn.MathUtils;
import ru.itmo.ipkn.Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ThreadLocalCollector implements MetricsCollector {

    private final int bucketCount;
    private final int bucketSize;

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> myState;

    public ThreadLocalCollector() {
        this(DEFAULT_BUCKET_COUNT, DEFAULT_BUCKET_SIZE);
    }

    public ThreadLocalCollector(int bucketCount, int bucketSize) {
        if (bucketCount < 1) {
            throw new IllegalArgumentException("Count of buckets should be more than 0");
        }
        if (bucketSize < 1) {
            throw new IllegalArgumentException("Size of buckets should be more than 0");
        }

        this.bucketCount = bucketCount;
        this.bucketSize = bucketSize;
        this.myState = ThreadLocal.withInitial(() -> {
            ThreadState s = new ThreadState(bucketCount);
            synchronized (listLock) {
                allStates.add(s);
            }
            return s;
        });
    }

    @Override
    public void record(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value should be non-negative");
        }

        ThreadState s = myState.get();
        int bucket = (int) Math.min(value / bucketSize, s.buckets.length() - 1);

        s.buckets.setRelease(bucket, s.buckets.getPlain(bucket) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);

        if (value < s.min.getPlain()) {
            s.min.setRelease(value);
        }
        if (value > s.max.getPlain()) {
            s.max.setRelease(value);
        }
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> statesCopy;
        synchronized (listLock) {
            statesCopy = new ArrayList<>(allStates);
        }

        long[] out = new long[bucketCount];
        long count = 0, sum = 0, min = Long.MAX_VALUE, max = 0;

        for (ThreadState s : statesCopy) {
            for (int i = 0; i < out.length; ++i) {
                out[i] += s.buckets.get(i);
            }
            count += s.count.get();

            sum += s.sum.get();
            min = Math.min(min, s.min.get());
            max = Math.max(max, s.max.get());
        }

        if (count == 0) {
            return new Snapshot(out, 0, 0, 0, 0, 0, 0);
        }

        return Snapshot.builder()
                .buckets(out)
                .count(count)
                .sum(sum)
                .min(min)
                .max(max)
                .p50(MathUtils.computePercentile(out, bucketSize, count, P50))
                .p99(MathUtils.computePercentile(out, bucketSize, count, P99))
                .build();
    }

    static final class ThreadState {
        final AtomicLongArray buckets;
        final AtomicLong count = new AtomicLong(0);
        final AtomicLong sum = new AtomicLong(0);
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);

        ThreadState(int bucketCount) {
            buckets = new AtomicLongArray(bucketCount);
        }
    }
}
