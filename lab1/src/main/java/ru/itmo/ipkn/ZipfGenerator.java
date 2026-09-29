package ru.itmo.ipkn;

import java.util.Arrays;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class ZipfGenerator {

    private static final String DEFAULT_RANDOM_ALGORITHM = "L64X128MixRandom";
    private static final long DEFAULT_RANDOM_SEED = 27;
    private static final int DEFAULT_MAX_VALUE = 1023;
    private static final double ZIPF_EXPONENT = 1.15;

    private final RandomGenerator random;
    private final double[] zipfCdf;

    public ZipfGenerator() {
        this(DEFAULT_RANDOM_SEED);
    }

    public ZipfGenerator(long seed) {
        this(seed, DEFAULT_MAX_VALUE);
    }

    public ZipfGenerator(long seed, int maxValue) {
        this.random = RandomGeneratorFactory.of(DEFAULT_RANDOM_ALGORITHM).create(seed);
        this.zipfCdf = createZipfCdf(maxValue, ZIPF_EXPONENT);
    }

    public int nextInt() {
        double u = random.nextDouble();
        int index = Arrays.binarySearch(zipfCdf, 1, zipfCdf.length, u);

        if (index < 0) {
            index = -(index + 1);
        }

        return index;
    }

    public int[] generateSequence(int length) {
        int[] sequence = new int[length];

        for (int i = 0; i < length; ++i) {
            sequence[i] = nextInt();
        }

        return sequence;
    }

    /**
     * Вычисление кумулятивной функции распределения по закону Ципфа
     *
     * @return массив, элементы которого являются суммой вероятностей предыдущих значений
     */
    private static double[] createZipfCdf(int maxValue, double zipfExponent) {
        double[] cdf = new double[maxValue + 1];

        // префиксные суммы вероятностей
        for (int k = 1; k <= maxValue; ++k) {
            cdf[k] = cdf[k - 1] + 1.0 / Math.pow(k, zipfExponent);
        }
        // нормализация
        for (int k = 1; k <= maxValue; ++k) {
            cdf[k] /= cdf[maxValue];
        }

        return cdf;
    }
}
