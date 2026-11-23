package com.dispatchflow.access.domain.session;

import com.dispatchflow.access.domain.AccessError;
import com.dispatchflow.access.domain.AccessError.SessionRevoked;
import com.dispatchflow.access.domain.AccessError.StepUpRequired;
import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.account.UserAccount;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.annotations.OptimisticLock;
import org.jspecify.annotations.Nullable;

/**
 * One signed-in device of an account (DL §4.1): the account generation it began under, its own
 * generation, whether it is live, when it last gave the password, its contact times and its chain
 * of refresh tokens. Its writers are that device, the same user signing it out from another device
 * (FR-127), and Access's own listener on {@code SessionsRevoked}, which ends it with its account's
 * revocation ({@link #revokeWithAccount}).
 *
 * <p>Every rule is checked before the state changes, so a refused call changes nothing. One call
 * changes state while it refuses: {@link #exchange}, where a reused refresh token revokes the
 * session, and the tokens too old to matter are dropped before the token is judged. So it answers
 * with a {@link RefreshOutcome} instead of throwing, and the caller saves the session either way.
 *
 * <p>The chain keeps only the current token, the tokens issued in the last {@link
 * #TOKEN_KEPT_FOR}, and any successor a kept token names. A replayed token older than that is
 * unknown, and refused without revoking the session (NFR-88, ADR-104).
 *
 * <p>It is mapped for JPA (ADR-126): its key is its ID's UUID, its {@link SessionState} a kind and
 * the revocation's time, its refresh tokens child entities in the order they were issued, and its
 * contact times an ordered element collection that leaves the version alone (DL §4.1). Every
 * command ends by checking the invariants at rest that the load check runs.
 */
@Entity
@Table(schema = "access", name = "session")
public class Session extends AggregateRoot {

    /** How long a refresh token lasts from its issue; the session itself has no cap (NFR-87). */
    public static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(14);

    /** How long after its exchange a token presented again still gets a new one (NFR-178). */
    public static final Duration REFRESH_GRACE = Duration.ofSeconds(30);

    /** How long a token that is not current stays in the chain after its issue (ADR-104). */
    public static final Duration TOKEN_KEPT_FOR = REFRESH_TOKEN_LIFETIME.plus(REFRESH_GRACE);

    /** How long giving the password allows Admin-only operations (NFR-214). */
    public static final Duration STEP_UP_LIFETIME = Duration.ofMinutes(5);

    /** How long a contact time is kept, as long as a late report can arrive (NFR-280, NFR-235). */
    public static final Duration CONTACT_RETENTION = Duration.ofDays(14);

    /** The least time between two kept contact times of one session (NFR-280). */
    public static final Duration CONTACT_SPACING = Duration.ofMinutes(1);

    /** Which {@link SessionState} a session is in. */
    private enum StateKind {
        LIVE,
        REVOKED
    }

    @Id
    private UUID id;

    private AccountId account;

    private AccountGeneration accountGeneration;

    private SessionGeneration generation;

    @Enumerated(EnumType.STRING)
    private StateKind state;

    private @Nullable Instant revokedAt;

    private Instant openedAt;

    private Instant lastPasswordAt;

    // DL §4.1 lets keeping and dropping these leave the version alone (ADR-126).
    @ElementCollection
    @CollectionTable(schema = "access", name = "session_contact", joinColumns = @JoinColumn(name = "session_id"))
    @OrderColumn(name = "position", nullable = false)
    @Column(name = "contacted_at")
    @OptimisticLock(excluded = true)
    private List<Instant> contactTimes = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "session_id", nullable = false)
    @OrderColumn(name = "position", nullable = false)
    private List<RefreshToken> tokens = new ArrayList<>();

    // Not stored: whether this instance holds a change its save must version (ADR-126).
    @Transient
    private boolean changedBeyondContactTimes;

    /** For Hibernate, which sets the fields from the stored rows and then runs {@link #checkStored}. */
    protected Session() {}

    private Session(
            SessionId id,
            AccountId account,
            AccountGeneration accountGeneration,
            RefreshTokenId tokenId,
            TokenHash tokenHash,
            Instant now) {
        this.id = id.value();
        this.account = account;
        this.accountGeneration = accountGeneration;
        this.generation = SessionGeneration.INITIAL;
        this.state = StateKind.LIVE;
        this.openedAt = now;
        this.lastPasswordAt = now;
        this.tokens.add(new RefreshToken(tokenId, tokenHash, now, new TokenState.Current()));
        this.changedBeyondContactTimes = true;
    }

    /** Builds the session {@link #restore} checks; it goes with {@code restore} (ADR-126). */
    private Session(SessionSnapshot stored) {
        super(stored.version());
        this.id = stored.id().value();
        this.account = stored.account();
        this.accountGeneration = stored.accountGeneration();
        this.generation = stored.generation();
        this.state = stored.state() instanceof SessionState.Revoked ? StateKind.REVOKED : StateKind.LIVE;
        this.revokedAt = stored.state() instanceof SessionState.Revoked(Instant at) ? at : null;
        this.openedAt = stored.openedAt();
        this.lastPasswordAt = stored.lastPasswordAt();
        this.contactTimes.addAll(stored.contactTimes());
        stored.tokens().forEach(token -> tokens.add(new RefreshToken(token)));
    }

    /**
     * Signs a device in, once the sign-in adapter has checked the password (NFR-22). Giving the
     * password to sign in counts as giving it for Admin-only operations (NFR-214).
     *
     * <p>The sign-in endpoint answers each refusal here as it answers a wrong password, after the
     * same password-hash work, so no caller learns whether the account exists or why it can't sign
     * in (NFR-145, NFR-225).
     *
     * @param id the new session's ID
     * @param account the account signing in
     * @param tokenId the first refresh token's ID
     * @param tokenHash the first refresh token's hash
     * @param now when
     * @return the live session, under the account's current generation, raising no event
     * @throws RuleViolation with {@link AccessError.AccountAnonymized}, {@link
     *     AccessError.AccountDeactivated} or {@link AccessError.AccountHasNoRole} when the account
     *     may not sign in (BR-123, FR-53), or with {@link AccessError.PasswordNotSet} when it has no
     *     password yet (FR-294)
     */
    public static Session open(
            SessionId id, UserAccount account, RefreshTokenId tokenId, TokenHash tokenHash, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(now, "now");
        account.checkSignInAllowed();
        return new Session(id, account.id(), account.generation(), tokenId, tokenHash, now);
    }

    /**
     * Rebuilds a stored session. Only the persistence adapter calls it (DL §3.5). It checks no
     * order between the token times, so a server clock that stepped back can't make a stored
     * session unreadable. Contact times may repeat, because {@link #recordContact} moves no
     * version and two requests that loaded the session before either saved can keep one instant
     * twice; they never decrease.
     *
     * @param stored the stored session
     * @return the session at its stored version
     * @throws StoredStateInvalid when it has no token; two tokens share an ID or a hash; a live
     *     session has not exactly one current token, or a revoked one has more than one; an
     *     exchanged token's successor is itself or not in the chain; or a contact time is earlier
     *     than the one before it
     */
    public static Session restore(SessionSnapshot stored) {
        Session restored = new Session(stored);
        restored.checkStored();
        return restored;
    }

    /**
     * Checks the session as Hibernate loaded it against the invariants at rest that {@link
     * #restore} checks (ADR-126 decision 8).
     *
     * @throws StoredStateInvalid when the stored state breaks one of them
     */
    @PostLoad
    void checkStored() {
        brokenRule().ifPresent(broken -> {
            throw new StoredStateInvalid(Session.class, broken);
        });
    }

    /**
     * Checks the state a command left against every invariant at rest (ADR-126 decision 11). Every
     * rule a caller can break was checked before the command changed anything, so a broken
     * invariant here is a bug in the command, not a refusal, and the error rolls its transaction
     * back.
     */
    private void requireInvariants() {
        brokenRule().ifPresent(broken -> {
            throw new AssertionError("a session command left a state that breaks: " + broken);
        });
    }

    /** The one check of the invariants at rest, shared by the load check and every command. */
    private Optional<String> brokenRule() {
        Optional<String> brokenChain = brokenTokenRule();
        if (brokenChain.isPresent()) {
            return brokenChain;
        }
        for (int i = 1; i < contactTimes.size(); i++) {
            if (contactTimes.get(i).isBefore(contactTimes.get(i - 1))) {
                return Optional.of("contact times never decrease");
            }
        }
        return Optional.empty();
    }

    /** The chain's part of {@link #brokenRule}. */
    private Optional<String> brokenTokenRule() {
        if (tokens.isEmpty()) {
            return Optional.of("a session holds at least one refresh token");
        }
        Set<RefreshTokenId> ids = new HashSet<>();
        Set<TokenHash> hashes = new HashSet<>();
        int current = 0;
        for (RefreshToken token : tokens) {
            if (!ids.add(token.id())) {
                return Optional.of("two refresh tokens share an ID");
            }
            if (!hashes.add(token.tokenHash())) {
                return Optional.of("two refresh tokens share a hash");
            }
            if (token.state() instanceof TokenState.Current) {
                current++;
            }
        }
        boolean live = state == StateKind.LIVE;
        if ((live && current != 1) || current > 1) {
            return Optional.of("a live session holds exactly one current token, a revoked one at most one");
        }
        for (RefreshToken token : tokens) {
            if (token.state() instanceof TokenState.Exchanged(Instant at, RefreshTokenId successor)
                    && (successor.equals(token.id()) || !ids.contains(successor))) {
                return Optional.of("an exchanged token's successor is another token of the chain");
            }
        }
        return Optional.empty();
    }

    /**
     * Exchanges a refresh token for the next one (NFR-22). The answer to every refusal is the same
     * (NFR-92):
     *
     * <ul>
     *   <li>the current token, under 14 days from its issue, is exchanged (NFR-87);
     *   <li>a token exchanged no more than 30 seconds ago, whose successor is still current, gets a
     *       new token that replaces that successor, and revokes nothing (NFR-178);
     *   <li>within those 30 seconds, a token whose successor a step-up superseded is refused, and
     *       revokes nothing (NFR-178, NFR-227);
     *   <li>any other exchanged token, and a replaced one, revokes the session (NFR-88, NFR-178);
     *   <li>a token a password confirmation superseded is refused, and revokes nothing (NFR-227);
     *   <li>so is any token once the session is revoked, or once the account's sessions were
     *       revoked after this one began (NFR-177), and an expired or unknown one, including one
     *       the chain no longer keeps (ADR-104).
     * </ul>
     *
     * <p>While the session is live and its account's generation stands, the chain first drops the
     * tokens older than {@link #TOKEN_KEPT_FOR} that are not current.
     *
     * @param presented the hash of the token presented
     * @param currentAccountGeneration the account's generation now
     * @param newTokenId the ID for the token a granted exchange issues, which no token of the chain
     *     holds
     * @param newTokenHash that token's hash, which no token of the chain holds
     * @param now when
     * @return {@link RefreshOutcome.Issued} with the new token, or {@link RefreshOutcome.Refused};
     *     save the session after either, because a refusal may have changed it
     * @throws AssertionError when a token of the chain already holds the new ID or hash: the use
     *     case reused one, a programming error rather than a refusal
     */
    public RefreshOutcome exchange(
            TokenHash presented,
            AccountGeneration currentAccountGeneration,
            RefreshTokenId newTokenId,
            TokenHash newTokenHash,
            Instant now) {
        Objects.requireNonNull(presented, "presented");
        Objects.requireNonNull(currentAccountGeneration, "currentAccountGeneration");
        Objects.requireNonNull(now, "now");
        requireNewToken(newTokenId, newTokenHash);
        if (state == StateKind.REVOKED || accountGeneration.isOlderThan(currentAccountGeneration)) {
            return RefreshOutcome.refused();
        }
        dropOldTokens(now);
        RefreshOutcome outcome = judged(presented, newTokenId, newTokenHash, now);
        requireInvariants();
        return outcome;
    }

    /** The presented token's exchange, judged on a live session whose old tokens are dropped. */
    private RefreshOutcome judged(TokenHash presented, RefreshTokenId newTokenId, TokenHash newTokenHash, Instant now) {
        Optional<RefreshToken> found = tokenHashed(presented);
        if (found.isEmpty()) {
            return RefreshOutcome.refused();
        }
        RefreshToken token = found.get();
        return switch (token.state()) {
            case TokenState.Current current -> {
                if (!now.isBefore(token.issuedAt().plus(REFRESH_TOKEN_LIFETIME))) {
                    yield RefreshOutcome.refused();
                }
                move(token, new TokenState.Exchanged(now, newTokenId));
                yield issue(newTokenId, newTokenHash, now);
            }
            case TokenState.Exchanged exchanged -> repeated(token, exchanged, newTokenId, newTokenHash, now);
            case TokenState.Replaced replaced -> revokedByReuse(now);
            case TokenState.Superseded superseded -> RefreshOutcome.refused();
        };
    }

    /** A token presented again after its exchange: the grace of NFR-178, or the reuse of NFR-88. */
    private RefreshOutcome repeated(
            RefreshToken token,
            TokenState.Exchanged exchanged,
            RefreshTokenId newTokenId,
            TokenHash newTokenHash,
            Instant now) {
        if (now.isAfter(exchanged.at().plus(REFRESH_GRACE))) {
            return revokedByReuse(now);
        }
        Optional<RefreshToken> successor = tokenNamed(exchanged.successor());
        if (successor.isPresent() && successor.get().state() instanceof TokenState.Superseded) {
            return RefreshOutcome.refused();
        }
        if (successor.isEmpty() || !(successor.get().state() instanceof TokenState.Current)) {
            return revokedByReuse(now);
        }
        move(successor.get(), new TokenState.Replaced(now));
        move(token, new TokenState.Exchanged(exchanged.at(), newTokenId));
        return issue(newTokenId, newTokenHash, now);
    }

    /** {@link #restore} refuses a repeated ID or hash, so a reuse fails before it is saved. */
    private void requireNewToken(RefreshTokenId newTokenId, TokenHash newTokenHash) {
        Objects.requireNonNull(newTokenId, "newTokenId");
        Objects.requireNonNull(newTokenHash, "newTokenHash");
        if (tokenNamed(newTokenId).isPresent()) {
            throw new AssertionError("a new token needs a new ID");
        }
        if (tokenHashed(newTokenHash).isPresent()) {
            throw new AssertionError("a new token needs a new hash");
        }
    }

    private RefreshOutcome issue(RefreshTokenId newTokenId, TokenHash newTokenHash, Instant now) {
        tokens.add(new RefreshToken(newTokenId, newTokenHash, now, new TokenState.Current()));
        changedBeyondContactTimes = true;
        return new RefreshOutcome.Issued(newTokenId);
    }

    private RefreshOutcome revokedByReuse(Instant now) {
        revokedAs(now);
        return RefreshOutcome.refused();
    }

    /**
     * Drops the tokens that are not current and were issued more than {@link #TOKEN_KEPT_FOR} ago,
     * except a successor that a kept token names. A clock that steps back can issue a successor
     * before the token it succeeds, and dropping it would leave a saved chain that {@link #restore}
     * refuses.
     */
    private void dropOldTokens(Instant now) {
        Instant keptSince = now.minus(TOKEN_KEPT_FOR);
        Set<RefreshTokenId> kept = new HashSet<>();
        for (RefreshToken token : tokens) {
            if (token.state() instanceof TokenState.Current || !token.issuedAt().isBefore(keptSince)) {
                kept.add(token.id());
            }
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (RefreshToken token : tokens) {
                if (kept.contains(token.id())
                        && token.state() instanceof TokenState.Exchanged exchanged
                        && kept.add(exchanged.successor())) {
                    grew = true;
                }
            }
        }
        if (tokens.removeIf(token -> !kept.contains(token.id()))) {
            changedBeyondContactTimes = true;
        }
    }

    /**
     * Records that the session gave the current password, which the caller has checked, for an
     * Admin-only operation (NFR-214). The session gets new tokens under a new generation, and its
     * older ones are refused (NFR-227). Tokens older than {@link #TOKEN_KEPT_FOR} that are not
     * current are dropped.
     *
     * @param currentAccountGeneration the account's generation now
     * @param newTokenId the new refresh token's ID, which no token of the chain holds
     * @param newTokenHash its hash, which no token of the chain holds
     * @param now when
     * @throws RuleViolation with {@link SessionRevoked} when the session is revoked, or the
     *     account's sessions were revoked after it began (NFR-177)
     * @throws AssertionError when a token of the chain already holds the new ID or hash: the use
     *     case reused one, a programming error rather than a refusal
     */
    public void confirmPassword(
            AccountGeneration currentAccountGeneration,
            RefreshTokenId newTokenId,
            TokenHash newTokenHash,
            Instant now) {
        Objects.requireNonNull(now, "now");
        requireNewToken(newTokenId, newTokenHash);
        requireLive(currentAccountGeneration);
        dropOldTokens(now);
        tokens.stream()
                .filter(token -> token.state() instanceof TokenState.Current)
                .forEach(token -> move(token, new TokenState.Superseded(now)));
        issue(newTokenId, newTokenHash, now);
        generation = generation.next();
        lastPasswordAt = now;
        requireInvariants();
    }

    /**
     * Checks the session gave its password in the last 5 minutes, before an operation the
     * permission matrix gives only to Admins, which {@link
     * com.dispatchflow.access.domain.account.Capability#requiresStepUp} names (NFR-214, NFR-227).
     *
     * @param currentAccountGeneration the account's generation now
     * @param now when the operation is asked for
     * @throws RuleViolation with {@link SessionRevoked} when the session is revoked, or the
     *     account's sessions were revoked after it began (NFR-177); or with {@link StepUpRequired}
     *     when the password was given more than 5 minutes ago
     */
    public void requireFreshStepUp(AccountGeneration currentAccountGeneration, Instant now) {
        Objects.requireNonNull(now, "now");
        requireLive(currentAccountGeneration);
        if (now.isAfter(lastPasswordAt.plus(STEP_UP_LIFETIME))) {
            throw new RuleViolation(new StepUpRequired());
        }
    }

    /**
     * Keeps the time of a request from the session, which BR-133's contact window reads (NFR-280).
     * The caller calls it only for an account with the Driver role, and only for a request other
     * than a token refresh or an outbox send. After {@link #dropOldContacts dropping} the times
     * older than 14 days, it keeps the time unless it is less than a minute after the last one
     * kept, which includes a time before it once a clock stepped back. So it never makes the times
     * decrease.
     *
     * <p>It is the one save that doesn't move the version, and it raises no event (DL §4.1): the
     * adapter writes the contact times alone, so recording a contact can never make a refresh
     * fail on a stale version. Two requests that loaded the session before either saved can each
     * keep a time, the same instant included, which {@link #restore} accepts.
     *
     * @param now when the request arrived
     * @throws RuleViolation with {@link SessionRevoked} when the session is revoked, because no
     *     request from it is accepted
     */
    public void recordContact(Instant now) {
        Objects.requireNonNull(now, "now");
        if (state == StateKind.REVOKED) {
            throw new RuleViolation(new SessionRevoked());
        }
        dropOldContacts(now);
        boolean spaced = contactTimes.isEmpty()
                || !now.isBefore(contactTimes.get(contactTimes.size() - 1).plus(CONTACT_SPACING));
        if (spaced) {
            contactTimes.add(now);
        }
        requireInvariants();
    }

    /**
     * Drops the contact times older than 14 days (NFR-280), in any state. The nightly retention
     * job calls it, so old times go without waiting for a new contact. Like {@link #recordContact},
     * it doesn't move the version.
     *
     * @param now when the job runs
     */
    public void dropOldContacts(Instant now) {
        Instant keptSince = now.minus(CONTACT_RETENTION);
        contactTimes.removeIf(time -> time.isBefore(keptSince));
        requireInvariants();
    }

    /**
     * Signs the session out, which revokes it (NFR-89, FR-127). Signing out again changes nothing,
     * so a repeated sign-out is harmless.
     *
     * @param now when
     */
    public void revoke(Instant now) {
        Objects.requireNonNull(now, "now");
        if (state == StateKind.LIVE) {
            revokedAs(now);
            requireInvariants();
        }
    }

    /**
     * Ends the session because its account's sessions were revoked (NFR-90, NFR-177), dated by
     * that revocation, so the retention job deletes it 30 days after the revocation rather than
     * after its token's expiry (NFR-258). Access's listener on {@code SessionsRevoked} calls it for
     * the account's live sessions. The token check refuses the session's tokens from the
     * revocation on, whether or not this has run.
     *
     * <p>Delivery is at least once, so it changes nothing on a session begun under the generation
     * the revocation left, which a redelivered event must not end. A session already revoked keeps
     * the earlier of its own revocation and this one: one signed out after its account's revocation,
     * but before this ran, ended when the account's sessions were revoked.
     *
     * @param revokedTo the account's generation after the revocation, which the event carries
     * @param revokedAt when the account's sessions were revoked
     */
    public void revokeWithAccount(AccountGeneration revokedTo, Instant revokedAt) {
        Objects.requireNonNull(revokedTo, "revokedTo");
        Objects.requireNonNull(revokedAt, "revokedAt");
        if (!accountGeneration.isOlderThan(revokedTo)) {
            return;
        }
        if (!(state() instanceof SessionState.Revoked revoked) || revokedAt.isBefore(revoked.at())) {
            revokedAs(revokedAt);
            requireInvariants();
        }
    }

    /**
     * Whether a command other than {@link #recordContact} and {@link #dropOldContacts} changed this
     * instance since it was created or loaded, which the adapter's save asks: a session whose
     * change only kept or dropped contact times is saved without moving its version (DL §4.1).
     *
     * @return {@code true} for a session never saved, and for one whose tokens, state, generation
     *     or password time changed since it was loaded
     */
    public boolean changedBeyondContactTimes() {
        return changedBeyondContactTimes;
    }

    private void requireLive(AccountGeneration currentAccountGeneration) {
        Objects.requireNonNull(currentAccountGeneration, "currentAccountGeneration");
        if (state == StateKind.REVOKED || accountGeneration.isOlderThan(currentAccountGeneration)) {
            throw new RuleViolation(new SessionRevoked());
        }
    }

    private void revokedAs(Instant at) {
        state = StateKind.REVOKED;
        revokedAt = at;
        changedBeyondContactTimes = true;
    }

    private void move(RefreshToken token, TokenState next) {
        token.moveTo(next);
        changedBeyondContactTimes = true;
    }

    private Optional<RefreshToken> tokenHashed(TokenHash hash) {
        return tokens.stream().filter(token -> token.tokenHash().equals(hash)).findFirst();
    }

    private Optional<RefreshToken> tokenNamed(RefreshTokenId tokenId) {
        return tokens.stream().filter(token -> token.id().equals(tokenId)).findFirst();
    }

    /**
     * When the session ended, or ends unless it refreshes first: the earlier of its revocation and
     * its current token's expiry. The retention job deletes a session 30 days after this (NFR-258).
     * A session its account's revocation ended is dated by that revocation once {@link
     * #revokeWithAccount} has run; until then its current token's expiry bounds it.
     *
     * @return the end time
     */
    public Instant endedAt() {
        Optional<Instant> expiry = tokens.stream()
                .filter(token -> token.state() instanceof TokenState.Current)
                .map(token -> token.issuedAt().plus(REFRESH_TOKEN_LIFETIME))
                .findFirst();
        return Stream.of(expiry, Optional.ofNullable(revokedAt))
                .flatMap(Optional::stream)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new AssertionError("a live session always holds a current token"));
    }

    /**
     * The session's ID.
     *
     * @return the ID
     */
    public SessionId id() {
        return SessionId.of(id);
    }

    /**
     * The account the session signed in.
     *
     * @return the account's ID
     */
    public AccountId account() {
        return account;
    }

    /**
     * The account's generation when the session began. Once the account's generation is newer,
     * the session's tokens are refused (NFR-177).
     *
     * @return the generation it began under
     */
    public AccountGeneration accountGeneration() {
        return accountGeneration;
    }

    /**
     * The session's own generation, which every access token it is issued carries (NFR-227).
     *
     * @return the current generation
     */
    public SessionGeneration generation() {
        return generation;
    }

    /**
     * Where the session stands.
     *
     * @return live or revoked
     */
    public SessionState state() {
        return switch (state) {
            case LIVE -> new SessionState.Live();
            case REVOKED ->
                new SessionState.Revoked(Objects.requireNonNull(revokedAt, "a revoked session has its revocation"));
        };
    }

    /**
     * The session as it would be stored.
     *
     * @return a snapshot of every field, the contact times oldest first and the tokens in the order
     *     they were issued
     */
    public SessionSnapshot snapshot() {
        return new SessionSnapshot(
                savedVersion().orElse(null),
                id(),
                account,
                accountGeneration,
                generation,
                state(),
                openedAt,
                lastPasswordAt,
                contactTimes,
                tokens.stream().map(RefreshToken::snapshot).toList());
    }
}
