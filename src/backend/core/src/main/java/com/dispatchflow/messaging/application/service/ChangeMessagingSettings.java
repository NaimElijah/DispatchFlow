package com.dispatchflow.messaging.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.StepUp;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsRepository;
import com.dispatchflow.messaging.domain.DispatchPhone;
import com.dispatchflow.messaging.domain.Switch;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * An Admin changes the messaging part of the company settings: messages on or off, the rating
 * question on or off, and the dispatch phone (FR-125, FR-199, FR-350, FR-375, FR-382, placement P7).
 * A change applies to the messages triggered after it (FR-350). One transaction calls {@code
 * MessagingSettings.changeMessages}, {@code changeRatingQuestion} and {@code changeDispatchPhone},
 * or creates the settings on the first change, with the audit record (FR-186).
 *
 * <p>The change is Admin-only, so before anything else it asks Access's {@link StepUp} that the
 * acting session gave its password within the step-up window (NFR-214, ADR-068). Like every
 * changing call it writes its audit record, a refused attempt's in its own transaction (NFR-23,
 * ADR-110).
 *
 * <p>Built in M12. Rows: FR-199, FR-350, FR-375, FR-382, NFR-214.
 */
public final class ChangeMessagingSettings {

    private final MessagingSettingsRepository settings;
    private final StepUp stepUp;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param settings where the messaging settings are kept
     * @param stepUp Access's recent-password check
     * @param clock the server's time, for the audit record
     * @param auditLog the audit trail
     */
    public ChangeMessagingSettings(
            MessagingSettingsRepository settings, StepUp stepUp, Clock clock, AuditLog auditLog) {
        this.settings = settings;
        this.stepUp = stepUp;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Sets the three settings.
     *
     * <p>Built in M12.
     *
     * @param actor the Admin
     * @param messages whether messages to recipients go
     * @param ratingQuestion whether the delivered message asks for a rating
     * @param dispatchPhone the dispatch phone, already checked by {@code DispatchPhone.of}, so at
     *     most 11 characters in national form (FR-382)
     * @param expected the settings' version the Admin saw, empty before the first
     *     change (BR-76)
     * @throws RuleViolation with Access's {@code SessionRevoked} or {@code StepUpRequired}, in that
     *     order, from {@link StepUp#requireFresh}, before any change (NFR-214)
     * @throws StaleVersion when the settings changed since {@code expected}
     */
    public void change(
            Actor actor,
            Switch messages,
            Switch ratingQuestion,
            DispatchPhone dispatchPhone,
            Optional<Version> expected) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
