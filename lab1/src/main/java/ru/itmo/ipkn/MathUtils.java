package ru.itmo.ipkn;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public class MathUtils {

    public static double median(List<? extends Number> list) {
        return list.stream().mapToDouble(Number::doubleValue).boxed()
                .sorted()
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        nums -> {
                            int middle = nums.size() / 2;
                            if (nums.size() % 2 == 0) {
                                return (nums.get(middle - 1) + nums.get(middle)) / 2;
                            } else {
                                return nums.get(middle);
                            }
                        }));
    }

    public static long computePercentile(long[] buckets, int bucketSize, long count, int percentile) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("Percentile must be between 0 and 100");
        }

        long threshold = (long) Math.ceil(count * percentile / 100.0);
        long sum = 0;
        for (int i = 0; i < buckets.length; ++i) {
            sum += buckets[i];
            if (sum >= threshold) {
                return (long) i * bucketSize;
            }
        }

        return (long) (buckets.length - 1) * bucketSize;
    }
}
