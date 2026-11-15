package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.Clock;

/**
 * The nightly retention job's session step: it deletes every session 30 days after it ended
 * (NFR-258), and drops the contact times older than 14 days from the rest, through {@code
 * Session.dropOldContacts}, without waiting for a new contact (NFR-280).
 *
 * <p>Built in an unscheduled milestone.
 */
public final class PurgeSessions {

    private final SessionRepository sessions;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param sessions where sessions are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public PurgeSessions(SessionRepository sessions, Clock clock, AuditLog auditLog) {
        this.sessions = sessions;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Deletes the ended sessions and drops the old contact times.
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param call the job's server step
     * @return how many sessions were deleted
     */
    public int purge(CallContext call) {
        throw new UnsupportedOperationException("Built in an unscheduled milestone");
    }
}
