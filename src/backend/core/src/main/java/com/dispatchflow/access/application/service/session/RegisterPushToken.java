package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.PushAddress;
import com.dispatchflow.access.application.port.out.PushTokens;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.application.Clock;

/**
 * The acting phone registers the push service's token for its device, so notices reach it (ADR-026,
 * ADR-083, placement P1). It replaces the session's earlier address, and is removed at sign-out and
 * revocation (NFR-263). Like every changing call it writes its audit record, a refused attempt's in
 * its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M10.
 */
public final class RegisterPushToken {

    private final PushTokens pushTokens;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param pushTokens the push addresses of signed-in phones
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RegisterPushToken(PushTokens pushTokens, Clock clock, AuditLog auditLog) {
        this.pushTokens = pushTokens;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Registers the address.
     *
     * <p>Built in M10.
     *
     * @param actor the signed-in user acting, whose call always names its session, since {@code
     *     Actor} refuses one that names none
     * @param address the push service's token for the device
     */
    public void register(Actor actor, PushAddress address) {
        throw new UnsupportedOperationException("Built in M10");
    }
}
