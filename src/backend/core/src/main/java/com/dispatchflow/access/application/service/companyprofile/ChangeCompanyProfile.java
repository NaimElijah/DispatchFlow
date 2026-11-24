package com.dispatchflow.access.application.service.companyprofile;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.CompanyProfileRepository;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.companyprofile.CompanyName;
import com.dispatchflow.access.domain.companyprofile.DefaultServiceTime;
import com.dispatchflow.access.domain.companyprofile.DefaultWindow;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * An Admin changes the company's settings in one place: its name, its default language, and the
 * time window and service duration a new customer starts with (FR-125, FR-182, FR-184). The name
 * keeps every message to one SMS (FR-382). Existing users and customers keep their own values. Each
 * change is audited (FR-186, NFR-23).
 *
 * <p>The operation is Admin-only in the permission matrix, so the acting session must have given
 * its password in the last 5 minutes, which {@code Session.requireFreshStepUp} checks (NFR-214,
 * NFR-227). The step-up arrives in M6.
 *
 * <p><b>The first save creates the profile.</b> While no profile exists, the use case creates it
 * with {@code CompanyProfile.create} from the command's name, language and window, sets the
 * service time when one is given, and adds it. The caller, having read no profile and so no
 * version, sends {@code If-None-Match: *}, which the web adapter passes as an empty version.
 * From then on it changes the stored profile, and each save sends {@code If-Match} with the version
 * it read.
 *
 * <p>{@code CompanyProfile} can't clear the default service time yet, so an empty one leaves the
 * current value; M2 adds the clearing method.
 *
 * <p>Built in M2.
 */
@Transactional
public class ChangeCompanyProfile {

    private final CompanyProfileRepository companyProfiles;
    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param companyProfiles where the company profile is kept
     * @param accounts where accounts are kept
     * @param sessions where sessions are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ChangeCompanyProfile(
            CompanyProfileRepository companyProfiles,
            UserAccountRepository accounts,
            SessionRepository sessions,
            Clock clock,
            AuditLog auditLog) {
        this.companyProfiles = companyProfiles;
        this.accounts = accounts;
        this.sessions = sessions;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the company's settings.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param command the settings
     * @param expected the version of the profile the caller last read, from {@code If-Match}; or
     *     empty when it read none and sent {@code If-None-Match: *} (BR-76)
     * @throws RuleViolation from M6, with {@code StepUpRequired} when the acting session gave its
     *     password more than 5 minutes ago, or {@code SessionRevoked} when it is revoked (NFR-214)
     * @throws StaleVersion when the profile is no longer at {@code expected}, a profile exists and
     *     {@code expected} is empty, or another Admin stored one before the save
     *     (BR-76)
     */
    public void change(Actor actor, Command command, Optional<Version> expected) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * The company's settings as the Admin saves them (FR-125, FR-184).
     *
     * <p>Built in M2.
     *
     * @param name the company's name (FR-382)
     * @param language the language a new user starts with (FR-182)
     * @param defaultWindow the time window a new customer starts with
     * @param defaultServiceTime the service duration a new customer starts with, or empty to leave
     *     none (FR-184)
     */
    public record Command(
            CompanyName name,
            Language language,
            DefaultWindow defaultWindow,
            Optional<DefaultServiceTime> defaultServiceTime) {

        /** Checks every part is present, possibly empty. */
        public Command {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(defaultWindow, "defaultWindow");
            Objects.requireNonNull(defaultServiceTime, "defaultServiceTime");
        }
    }
}
