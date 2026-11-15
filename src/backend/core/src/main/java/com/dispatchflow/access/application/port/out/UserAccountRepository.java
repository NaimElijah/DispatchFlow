package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.account.ActiveAdmins;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.EmailHolder;
import com.dispatchflow.access.domain.account.UserAccount;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where user accounts are kept, whole, with their credential links (DL §3.6, §4.1). The unique
 * index on the canonical email backs BR-45 when two requests race (DL §5).
 *
 * <p>Built in M2, M5, M6 and M13, method by method.
 */
public interface UserAccountRepository {

    /**
     * The account with an ID.
     *
     * <p>Built in M2.
     *
     * @param id the account's ID
     * @return the account, or empty when no account has the ID
     */
    Optional<UserAccount> get(AccountId id);

    /**
     * An active account's generation alone, which the token check compares with a token's {@code
     * ugen} on every request (ADR-045, NFR-177). It reads no aggregate. The adapter answers it
     * through the revocation cache's atomic loader, so the cache sits in the adapter and {@code
     * SessionValidity} keeps one implementation; {@link RevocationCache#evict(AccountId)} drops the
     * entry after a change.
     *
     * <p>Built in M6.
     *
     * @param account the token's account
     * @return the account's generation, or empty when no account has the ID or it is not active
     */
    Optional<AccountGeneration> activeGenerationOf(AccountId account);

    /**
     * The account that signs in with an address (FR-296, BR-45).
     *
     * <p>Built in M5.
     *
     * @param email the address, in its canonical form
     * @return the account, or empty when none holds it; an anonymized account holds none
     */
    Optional<UserAccount> withEmail(EmailAddress email);

    /**
     * Which account holds an address now, read by the unique index before an account is opened or a
     * new address confirmed (BR-45, FR-392, DL §5).
     *
     * <p>Built in M6.
     *
     * @param email the address
     * @return the holder, or no one, for that address
     */
    EmailHolder holderOf(EmailAddress email);

    /**
     * The active Admin accounts, read under lock, which BR-19's and FR-291's checks require (DL
     * §5). It takes a transaction-scoped advisory lock and then {@code FOR UPDATE} on the active
     * Admin rows, so a read that finds none still excludes a concurrent first-Admin step. It runs
     * in the transaction that saves the change.
     *
     * <p>Built in M6.
     *
     * @return the snapshot, the only way one is built (DL §3.9 rule 6)
     */
    ActiveAdmins activeAdminsUnderLock();

    /**
     * The address and language of every active account that holds the Admin role, which FR-389's
     * notice goes to. It reads in the caller's transaction, after the change is applied, so an
     * account that has just gained the Admin role or been reactivated is among them. It takes no
     * lock: the notice is advice, not a rule.
     *
     * <p>Built in M6.
     *
     * @return the recipients, each once, in any order; empty when no Admin is active
     */
    List<AdminNotice.Recipient> activeAdminRecipients();

    /**
     * The account holding a credential link with a token hash, whatever the link's kind or status
     * (NFR-211).
     *
     * <p>Built in M6.
     *
     * @param link the hash of the token the link carried
     * @return the account, or empty when no account holds such a link
     */
    Optional<UserAccount> withLink(TokenHash link);

    /**
     * The accounts deactivated before an instant and not yet anonymized, which the retention job
     * anonymizes (NFR-258). It answers IDs, so each account is loaded, changed and saved in its own
     * transaction.
     *
     * <p>Built in M13.
     *
     * @param cutoff the latest deactivation that is due
     * @return their IDs, in any order
     */
    List<AccountId> deactivatedBefore(Instant cutoff);

    /**
     * The accounts that hold an open credential link, of any kind, which the restore's server step
     * voids (ADR-045, NFR-252). It answers IDs, so each account is loaded, changed and saved in its
     * own transaction.
     *
     * <p>Built in M13.
     *
     * @return their IDs, in any order
     */
    List<AccountId> withOpenLinks();

    /**
     * The accounts holding a credential link sent before an instant, of any kind or status, whose
     * old links the retention job drops (ADR-104). A link sent more than {@code
     * UserAccount.LINK_KEPT_FOR} ago no longer works, so the job's cutoff finds none that does. It
     * answers IDs, so each account is loaded, changed and saved in its own transaction.
     *
     * <p>Built in M13.
     *
     * @param cutoff the latest sending that is due
     * @return their IDs, in any order
     */
    List<AccountId> withLinksSentBefore(Instant cutoff);

    /**
     * Stores a new account and publishes the events it raised (DL §3.6).
     *
     * <p>Built in M6.
     *
     * @param account the new account, at {@code Version.NEW}
     * @throws RuleViolation with Access's {@code EmailInUse} when another account took the address
     *     after {@link #holderOf} read it: the unique index backs BR-45
     */
    void add(UserAccount account);

    /**
     * Stores a changed account with a version check, and publishes the events it raised in the same
     * transaction (DL §3.6, BR-76).
     *
     * <p>Built in M2.
     *
     * @param account the changed account
     * @throws StaleVersion when the stored account is no longer at the version it was loaded at
     * @throws RuleViolation with Access's {@code EmailInUse} when a confirmed address was taken by
     *     another account after {@link #holderOf} read it (BR-45, FR-392)
     */
    void save(UserAccount account);
}
