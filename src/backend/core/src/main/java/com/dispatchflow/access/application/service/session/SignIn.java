package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.PasswordHasher;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.TokenSigner;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * Signs a device in with an email address and a password (FR-296, FR-03): it checks the password,
 * opens a {@code Session} with its first refresh token, and signs the first access token (NFR-86,
 * NFR-87). Every attempt leaves an audit record, a refused one in its own transaction (NFR-23,
 * ADR-110).
 *
 * <p><b>One refusal for all</b> (NFR-145, NFR-225): an address no account holds, a wrong password,
 * and the four refusals of {@code UserAccount.checkSignInAllowed}, which {@code Session.open}
 * raises: {@code AccountDeactivated}, {@code AccountAnonymized}, {@code AccountHasNoRole} (BR-123)
 * and {@code PasswordNotSet} (FR-294). The use case throws {@link SignInRefused} for all six, after
 * the same password-hash work: {@code PasswordHasher.matches} for an account with a password hash,
 * and {@code PasswordHasher.checkDecoy} for an address no account holds and for an account with no
 * password hash, so a pending account answers as an unknown address does. The domain's specific
 * code goes only to {@code AuditLog.refused}, so no caller learns whether an address has an account
 * or why it can't sign in.
 *
 * <p>The attempt limits of NFR-35, NFR-216 and NFR-226 belong to the rate-limit adapter (ADR-071),
 * which counts typed addresses whether or not an account holds them (NFR-225). FR-299's warning is
 * read from {@code AccountQueries.me} after sign-in.
 *
 * <p>Built in M5.
 */
public final class SignIn {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final SecretTokens secretTokens;
    private final TokenSigner tokenSigner;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param accounts where accounts are kept
     * @param sessions where sessions are kept
     * @param passwordHasher hashes passwords and checks a presented one
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param tokenSigner signs access tokens
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public SignIn(
            UserAccountRepository accounts,
            SessionRepository sessions,
            PasswordHasher passwordHasher,
            SecretTokens secretTokens,
            TokenSigner tokenSigner,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.secretTokens = secretTokens;
        this.tokenSigner = tokenSigner;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Signs the device in.
     *
     * <p>Built in M5.
     *
     * @param email the address typed
     * @param password the password typed
     * @param call the call's context: no user or session yet, the client and the trace (ADR-110)
     * @return the new session and its tokens
     * @throws SignInRefused for each of the six refusals named above, which the endpoint answers as
     *     401
     */
    public SignedIn signIn(EmailAddress email, PresentedPassword password, CallContext call) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
