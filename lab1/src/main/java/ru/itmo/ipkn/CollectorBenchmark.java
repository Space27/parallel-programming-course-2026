package ru.itmo.ipkn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import ru.itmo.ipkn.collector.MetricsCollector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CollectorBenchmark {

    private final static int DEFAULT_WARMUP_TIME = 5;
    private final static int DEFAULT_MEASUREMENT_ITERATIONS = 5;
    private final static int DEFAULT_MEASUREMENT_TIME = 5;

    @Builder.Default
    private final int warmupTime = DEFAULT_WARMUP_TIME;
    @Builder.Default
    private final int measurementIterations = DEFAULT_MEASUREMENT_ITERATIONS;
    @Builder.Default
    private final int measurementTime = DEFAULT_MEASUREMENT_TIME;

    public long measurePoint(MetricsCollector collector, int[] values, int tCount) {
        run(collector, values, tCount, warmupTime);
        List<Long> results = new ArrayList<>();

        for (int i = 0; i < measurementIterations; ++i) {
            results.add(run(collector, values, tCount, measurementTime));
        }

        IO.println(collector.snapshot().count());
        return (long) MathUtils.median(results);
    }

    private long run(MetricsCollector collector, int[] values, int tCount, int seconds) {
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

        long t0 = System.nanoTime();
        start.countDown();
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        stop.set(true);
        double durationSeconds = (System.nanoTime() - t0) / 1_000_000_000.;

        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        return (long) (Arrays.stream(ops).sum() / durationSeconds);
    }
}
