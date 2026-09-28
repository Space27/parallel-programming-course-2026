package ru.itmo.ipkn.collector;

import ru.itmo.ipkn.Snapshot;

public class SynchronizedEmptyCollector extends SingleThreadCollector {

    public SynchronizedEmptyCollector() {
        super();
    }

    public SynchronizedEmptyCollector(int bucketCount, int bucketSize) {
        super(bucketCount, bucketSize);
    }

    @Override
    public synchronized void record(long value) {
    }

    @Override
    public synchronized Snapshot snapshot() {
        return super.snapshot();
    }
}
