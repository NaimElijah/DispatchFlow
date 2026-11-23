package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.application.port.out.AdminNotice;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.Role;
import com.dispatchflow.access.domain.account.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaUserAccountRepository}: {@code save}, and the lookups
 * and locks the port needs. The lookups that find an account answer its key alone, so that {@link
 * #findById} loads it whole.
 */
interface UserAccounts extends Repository<UserAccount, UUID> {

    /**
     * Saves an account and publishes its pending events.
     *
     * @param account the account
     * @return the managed account
     */
    UserAccount save(UserAccount account);

    /**
     * Loads an account with its links; {@link #withRoles} then loads its roles, so the two
     * collections don't multiply each other's rows.
     *
     * @param id the account's UUID
     * @return the account, or empty
     */
    @EntityGraph(attributePaths = "links")
    Optional<UserAccount> findById(UUID id);

    /**
     * Loads an account's roles into the account this transaction already holds.
     *
     * @param id the account's UUID
     * @return the account, or empty
     */
    @Query("select a from UserAccount a left join fetch a.roles where a.id = :id")
    Optional<UserAccount> withRoles(UUID id);

    /**
     * An active account's generation alone, as its stored count. A native query, because Spring
     * Data would read a JPQL query that returns the record as a projection to build.
     *
     * @param id the account's UUID
     * @return the count, or empty when no account has the key or it is not active
     */
    @Query(value = "SELECT generation FROM access.user_account WHERE id = :id AND state = 'ACTIVE'", nativeQuery = true)
    Optional<Long> activeGenerationOf(UUID id);

    /**
     * The account holding an address, by the unique email index.
     *
     * @param email the address, in its canonical form
     * @return the account's UUID, or empty
     */
    @Query("select a.id from UserAccount a where a.email = :email")
    Optional<UUID> holding(EmailAddress email);

    /**
     * Takes the active Admins' lock for the caller's transaction, waiting for it (ADR-127 decision
     * 9). It has one key, because the company has one set of Admins.
     *
     * @return 1, always: the call is made for its lock
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext('access:active-admins'))", nativeQuery = true)
    int lockActiveAdmins();

    /**
     * The active accounts holding the Admin role, each row locked for the caller's transaction.
     *
     * @return their UUIDs, in no order
     */
    @Query(value = """
            SELECT a.id FROM access.user_account a
            WHERE a.state = 'ACTIVE'
              AND EXISTS (
                  SELECT 1 FROM access.user_account_role r
                  WHERE r.user_account_id = a.id AND r.role = 'ADMIN')
            FOR UPDATE OF a
            """, nativeQuery = true)
    List<UUID> activeAdminsForUpdate();

    /**
     * The address and language of every active account holding a role.
     *
     * @param role the role, the Admin role for FR-389's notice
     * @return the recipients, each once, in no order
     */
    @Query("""
            select new com.dispatchflow.access.application.port.out.AdminNotice$Recipient(a.email, a.language)
            from UserAccount a join a.roles r
            where a.state = ACTIVE and r = :role
            """)
    List<AdminNotice.Recipient> activeHolders(Role role);

    /**
     * The account holding a link with a token hash, whatever the link's kind or status.
     *
     * @param tokenHash the link token's hash
     * @return the account's UUID, or empty
     */
    @Query("select a.id from UserAccount a join a.links l where l.tokenHash = :tokenHash")
    Optional<UUID> holdingLink(TokenHash tokenHash);

    /**
     * The accounts deactivated at or before an instant, and not anonymized.
     *
     * @param cutoff the latest deactivation that is due
     * @return their UUIDs, in no order
     */
    @Query("select a.id from UserAccount a where a.state = DEACTIVATED and a.deactivatedAt <= :cutoff")
    List<UUID> deactivatedBy(Instant cutoff);

    /**
     * The accounts holding an open link.
     *
     * @return their UUIDs, each once, in no order
     */
    @Query("select distinct a.id from UserAccount a join a.links l where l.status = OPEN")
    List<UUID> withOpenLinks();

    /**
     * The accounts holding a link sent at or before an instant, whatever its status.
     *
     * @param cutoff the latest sending that is due
     * @return their UUIDs, each once, in no order
     */
    @Query("select distinct a.id from UserAccount a join a.links l where l.issuedAt <= :cutoff")
    List<UUID> withLinksSentBy(Instant cutoff);
}
