package com.dispatchflow.web;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.AuditedRecord;
import com.dispatchflow.audit.application.api.CallContext;
import java.time.Instant;
import java.util.List;

/**
 * An audit log a test expects never to be written to: a standalone MockMvc setup runs no access
 * interceptor, so it keeps no caller, and the contract's handler records no refusal. It throws an
 * {@code IllegalStateException}, not an {@code AssertionError}: the contract's handler answers that
 * with a 500 every status assertion sees, where Spring would log an error thrown from an exception
 * handler at WARN and answer the original exception.
 */
final class NeverWrittenAuditLog implements AuditLog {

    @Override
    public void succeeded(CallContext call, Instant at, List<AuditedRecord> records) {
        throw new IllegalStateException("this test writes no audit record");
    }

    @Override
    public void refused(CallContext call, Instant at, List<AuditedRecord> records, String errorCode) {
        throw new IllegalStateException("this test writes no audit record");
    }
}
