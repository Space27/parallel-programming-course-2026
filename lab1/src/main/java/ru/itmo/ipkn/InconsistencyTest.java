package ru.itmo.ipkn;

import lombok.RequiredArgsConstructor;
import ru.itmo.ipkn.collector.MetricsCollector;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

@RequiredArgsConstructor
public class InconsistencyTest {

    private static final int DEFAULT_ITERATIONS = 50_000;

    private final int iterations;

    InconsistencyTest() {
        this(DEFAULT_ITERATIONS);
    }

    public TestResults run(MetricsCollector collector, int[] values, int tCount) {
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);
        long[] ops = new long[tCount];

        List<Thread> threads = IntStream.range(0, tCount)
                .mapToObj(k -> new Thread(() -> {
                    long localCount = 0L;
                    int i = k * 1000;
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    while (!stop.get()) {
                        collector.record(values[i]);
                        ++localCount;
                        if (++i == values.length) {
                            i = 0;
                        }
                    }
                    ops[k] = localCount;
                })).toList();

        threads.forEach(Thread::start);

        int brokenSnapshots = 0;
        int sumGreaterThanCount = 0;

        start.countDown();

        for (int i = 0; i < iterations; ++i) {
            Snapshot snapshot = collector.snapshot();
            long bucketsSum = Arrays.stream(snapshot.buckets()).sum();
            long snapCount = snapshot.count();

            if (bucketsSum != snapCount) {
                ++brokenSnapshots;
                if (bucketsSum > snapCount) {
                    ++sumGreaterThanCount;
                }
            }
        }

        stop.set(true);
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        long totalOpsRecorded = Arrays.stream(ops).sum();
        long totalOpsSnapshot = collector.snapshot().count();

        return new TestResults(
                brokenSnapshots * 100. / iterations,
                sumGreaterThanCount,
                brokenSnapshots - sumGreaterThanCount,
                totalOpsRecorded,
                totalOpsSnapshot
        );
    }

    public record TestResults(
            double brokenSnapshotsPercent,
            int sumGreater,
            int sumLess,
            long opsRecorded,
            long opsSnapshot
    ) {
    }
}