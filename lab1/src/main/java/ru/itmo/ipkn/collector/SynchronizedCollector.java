package ru.itmo.ipkn.collector;

import ru.itmo.ipkn.Snapshot;

public class SynchronizedCollector extends SingleThreadCollector {

    public SynchronizedCollector() {
        super();
    }

    public SynchronizedCollector(int bucketCount, int bucketSize) {
        super(bucketCount, bucketSize);
    }

    @Override
    public synchronized void record(long value) {
        super.record(value);
    }

    @Override
    public synchronized Snapshot snapshot() {
        return super.snapshot();
    }
}
