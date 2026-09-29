package ru.itmo.ipkn;

import ru.itmo.ipkn.collector.MetricsCollector;
import ru.itmo.ipkn.collector.DoubleBufferingCollector;

import java.util.List;


public class Main {

    static void main() {
        MetricsCollector collector = new DoubleBufferingCollector();
        List<Integer> threads = List.of(1, 2, 4, 6, 8, 12);
        int[] values = new ZipfGenerator().generateSequence(2 << 20);

        runInconsistencyTest(collector, values, 4);
        for (int tCount : threads) {
            runBenchmark(collector, values, tCount);
        }
    }

    private static void runInconsistencyTest(MetricsCollector collector, int[] values, int tCount) {
        InconsistencyTest test = new InconsistencyTest();

        InconsistencyTest.TestResults results = test.run(collector, values, tCount);

        System.out.printf(
                "Broken percent: %.2f\nSum < count: %d\nSum > count: %d\nCount - records: %d\n",
                results.brokenSnapshotsPercent(), results.sumLess(),
                results.sumGreater(), results.opsSnapshot() - results.opsRecorded()
        );
    }

    private static void runBenchmark(MetricsCollector collector, int[] values, int tCount) {
        CollectorBenchmark benchmark = new CollectorBenchmark();

        System.out.printf("%d threads: %.2f MIPS\n", tCount, benchmark.measurePoint(collector, values, tCount) / 1_000_000.);
    }
}
