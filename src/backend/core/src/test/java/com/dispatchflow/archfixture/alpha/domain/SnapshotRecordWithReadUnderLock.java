package com.dispatchflow.archfixture.alpha.domain;

/** A lock-read snapshot shaped as a record, whose canonical constructor anyone may call. */
public record SnapshotRecordWithReadUnderLock(int count) {

    public static SnapshotRecordWithReadUnderLock readUnderLock(int count) {
        return new SnapshotRecordWithReadUnderLock(count);
    }
}
