package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.application.port.out.AdminNotice;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.AccessError.EmailInUse;
import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.account.ActiveAdmins;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.EmailHolder;
import com.dispatchflow.access.domain.account.Role;
import com.dispatchflow.access.domain.account.UserAccount;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.adapter.out.persistence.OpenTransaction;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Keeps user accounts in {@code access.user_account}, their roles in {@code
 * access.user_account_role} and their credential links in {@code access.credential_link}.
 *
 * <p>The unique constraint on the canonical email backs BR-45 when two requests race: {@code add}
 * and {@code save} answer its violation with {@link EmailInUse}, and let every other constraint
 * violation through.
 *
 * <p>{@link #activeGenerationOf} reads the database on every call: the revocation cache the port
 * names is not built yet.
 */
@Component
class JpaUserAccountRepository implements UserAccountRepository {

    /** The migration's name for the unique constraint on the canonical email (BR-45). */
    private static final String EMAIL_UNIQUE = "user_account_email_unique";

    private final UserAccounts accounts;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param accounts the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which {@code add} flushes
     */
    JpaUserAccountRepository(UserAccounts accounts, VersionedSave versionedSave, EntityManager entityManager) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<UserAccount> get(AccountId id) {
        return load(id.value());
    }

    @Override
    public Optional<AccountGeneration> activeGenerationOf(AccountId account) {
        return accounts.activeGenerationOf(account.value()).map(AccountGeneration::of);
    }

    @Override
    public Optional<UserAccount> withEmail(EmailAddress email) {
        return accounts.holding(email).flatMap(this::load);
    }

    @Override
    public EmailHolder holderOf(EmailAddress email) {
        return accounts.holding(email)
                .map(holder -> EmailHolder.account(email, AccountId.of(holder)))
                .orElseGet(() -> EmailHolder.nobody(email));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The advisory lock comes first, so two first-Admin steps that both find no active Admin
     * still run one after the other (FR-291).
     */
    @Override
    public ActiveAdmins activeAdminsUnderLock() {
        OpenTransaction.require("activeAdminsUnderLock");
        accounts.lockActiveAdmins();
        return ActiveAdmins.readUnderLock(
                accounts.activeAdminsForUpdate().stream().map(AccountId::of).toList());
    }

    @Override
    public List<AdminNotice.Recipient> activeAdminRecipients() {
        return accounts.activeHolders(Role.ADMIN);
    }

    @Override
    public Optional<UserAccount> withLink(TokenHash link) {
        return accounts.holdingLink(link).flatMap(this::load);
    }

    @Override
    public List<AccountId> deactivatedBefore(Instant cutoff) {
        return ids(accounts.deactivatedBy(cutoff));
    }

    @Override
    public List<AccountId> withOpenLinks() {
        return ids(accounts.withOpenLinks());
    }

    @Override
    public List<AccountId> withLinksSentBefore(Instant cutoff) {
        return ids(accounts.withLinksSentBy(cutoff));
    }

    /**
     * {@inheritDoc}
     *
     * <p>It flushes at once, so the unique email constraint answers inside this call.
     */
    @Override
    public void add(UserAccount account) {
        try {
            accounts.save(account);
            entityManager.flush();
        } catch (ConstraintViolationException | DataIntegrityViolationException violation) {
            throw emailInUseOr(violation);
        }
    }

    @Override
    public void save(UserAccount account) {
        try {
            versionedSave.save(account, accounts::save);
        } catch (ConstraintViolationException | DataIntegrityViolationException violation) {
            throw emailInUseOr(violation);
        }
    }

    /** Loads an account whole: its links with it, then its roles into the same instance. */
    private Optional<UserAccount> load(UUID id) {
        Optional<UserAccount> account = accounts.findById(id);
        account.ifPresent(found -> accounts.withRoles(id));
        return account;
    }

    private static List<AccountId> ids(List<UUID> keys) {
        return keys.stream().map(AccountId::of).toList();
    }

    /**
     * {@link EmailInUse} for a violation of the unique email constraint, and the violation itself
     * for any other, which is a bug.
     */
    private static RuntimeException emailInUseOr(RuntimeException violation) {
        for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint
                    && EMAIL_UNIQUE.equals(constraint.getConstraintName())) {
                return new RuleViolation(new EmailInUse());
            }
        }
        return violation;
    }
}
