package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.api.PushAddress;
import com.dispatchflow.access.application.api.PushTargets;
import com.dispatchflow.access.application.port.out.session.PushTokens;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.application.Clock;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access's implementation of {@code PushTargets}: the push addresses of an account's live sessions
 * and of the staff's, for Live Operations' {@code PushNotifier} (FR-405, ADR-026, ADR-083,
 * placement P1), and the removal of an address the push service reported gone (NFR-263). {@link
 * #forget} removes it through {@code PushTokens.removeAddress} and writes its audit record, naming
 * each session it was removed from, in the same transaction, dated by the {@link Clock} (NFR-23,
 * ADR-110).
 *
 * <p>Built in M10.
 */
@Transactional
public class PushTargetsService implements PushTargets {

    private final PushTokens pushTokens;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the service.
     *
     * @param pushTokens the push addresses of signed-in phones
     * @param clock the time of a removal
     * @param auditLog where a removal's audit record is written
     */
    public PushTargetsService(PushTokens pushTokens, Clock clock, AuditLog auditLog) {
        this.pushTokens = pushTokens;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M10.
     */
    @Override
    public List<PushAddress> of(AccountId account) {
        throw new UnsupportedOperationException("Built in M10");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M10.
     */
    @Override
    public List<PushAddress> ofStaff() {
        throw new UnsupportedOperationException("Built in M10");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M10.
     */
    @Override
    public void forget(PushAddress address, CallContext call) {
        throw new UnsupportedOperationException("Built in M10");
    }
}
