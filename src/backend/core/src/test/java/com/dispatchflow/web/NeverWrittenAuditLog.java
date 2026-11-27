package com.dispatchflow.web;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.AuditedRecord;
import com.dispatchflow.audit.application.api.CallContext;
import java.time.Instant;
import java.util.List;

/**
 * An audit log a test expects never to be written to: a standalone MockMvc setup runs no access
 * interceptor, so it keeps no caller, and the contract's handler records no refusal.
 */
final class NeverWrittenAuditLog implements AuditLog {

    @Override
    public void succeeded(CallContext call, Instant at, List<AuditedRecord> records) {
        throw new AssertionError("this test writes no audit record");
    }

    @Override
    public void refused(CallContext call, Instant at, List<AuditedRecord> records, String errorCode) {
        throw new AssertionError("this test writes no audit record");
    }
}
