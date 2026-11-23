package com.dispatchflow.access.adapter.out.persistence;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.TestPersistenceConfiguration.RecordedEvents;
import com.dispatchflow.Transactions;
import com.dispatchflow.access.application.port.out.AdminNotice;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.AccessError.EmailInUse;
import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.account.AccountDescription;
import com.dispatchflow.access.domain.account.ActiveAdmins;
import com.dispatchflow.access.domain.account.CredentialLinkId;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.EmailHolder;
import com.dispatchflow.access.domain.account.LinkPurpose;
import com.dispatchflow.access.domain.account.PasswordHash;
import com.dispatchflow.access.domain.account.Role;
import com.dispatchflow.access.domain.account.RoleSet;
import com.dispatchflow.access.domain.account.UserAccount;
import com.dispatchflow.access.events.SessionsRevoked;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * {@link UserAccountRepository} against a real PostgreSQL (ADR-126, ADR-127): what an account
 * stores and loads as, its version, the unique email, the lookups, the active Admins and their
 * lock, and the stored states the load check refuses.
 *
 * <p>The database is shared and never cleaned, and some lookups read every account. So test {@code
 * n} keys its own accounts ({@code account(n)}, {@code account(100 + n)}, …), each with its own
 * address, and the tests that read every account keep to their own. Link {@code m} of account
 * {@code n} is {@code link(n, m)}. Each getter is read outside the transaction that loaded the
 * account, which shows the account was loaded whole.
 */
@IntegrationTest
class UserAccountPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_6163_6301L;
    private static final Instant T = Instant.parse("2031-03-10T08:00:00Z");
    private static final AccountId OTHER_ADMIN = AccountId.of(new UUID(KEY_PREFIX + 2, 1));
    private static final ActiveAdmins NO_ADMINS = ActiveAdmins.readUnderLock(List.of());
    private static final Duration A_YEAR_AND_A_DAY = Duration.ofDays(367);
    /** PostgreSQL's SQL state for a statement its lock timeout cancelled. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordedEvents events;

    // --- round trips (ADR-127 decision 1)

    /** The states an account stores and loads in, reached through commands. */
    enum Scenario {
        WITH_PASSWORD,
        NO_ROLE,
        EVERY_ROLE,
        NO_PASSWORD,
        TERMS_ACCEPTED,
        EMAIL_SET_BY_ANOTHER,
        DEACTIVATED_EMAIL_SET_BY_ANOTHER,
        LINKS_IN_EVERY_STATUS,
        DEACTIVATED,
        ANONYMIZED
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenAnAccount_WhenAddedAndLoadedInAnotherTransaction_ThenEveryPartComesBackAsAdded(Scenario scenario) {
        int n = 1 + scenario.ordinal();
        UserAccount added = transactions.inTransaction(() -> {
            UserAccount account = switch (scenario) {
                case WITH_PASSWORD -> withPassword(n);
                case NO_ROLE -> {
                    UserAccount driver = opened(n, Role.DRIVER);
                    driver.removeDriverRole(at(1));
                    yield driver;
                }
                case EVERY_ROLE -> opened(n, Role.ADMIN, Role.DISPATCHER, Role.DRIVER);
                case NO_PASSWORD -> opened(n, Role.DISPATCHER);
                case TERMS_ACCEPTED -> {
                    UserAccount accepted = withPassword(n);
                    accepted.changeLanguage(Language.ENGLISH);
                    accepted.acceptTerms(at(2));
                    yield accepted;
                }
                case EMAIL_SET_BY_ANOTHER -> emailSetByAnother(n);
                case DEACTIVATED_EMAIL_SET_BY_ANOTHER -> {
                    UserAccount moved = emailSetByAnother(n);
                    moved.deactivate(NO_ADMINS, at(10));
                    yield moved;
                }
                case LINKS_IN_EVERY_STATUS -> linksInEveryStatus(n);
                case DEACTIVATED -> deactivated(n);
                case ANONYMIZED -> anonymized(n);
            };
            repository.add(account);
            return account;
        });

        UserAccount loaded =
                transactions.inTransaction(() -> repository.get(account(n)).orElseThrow());

        assertThat(AccountDescription.of(loaded)).isEqualTo(AccountDescription.of(added));
        assertThat(loaded.id()).isEqualTo(account(n));
        assertThat(loaded.email()).isEqualTo(added.email());
        assertThat(loaded.roles()).isEqualTo(added.roles());
        assertThat(loaded.passwordHash()).isEqualTo(added.passwordHash());
        assertThat(loaded.state()).isEqualTo(added.state());
        assertThat(loaded.language()).isEqualTo(added.language());
        assertThat(loaded.generation()).isEqualTo(added.generation());
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenNoAccountWithAnId_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(account(11))))
                .isEmpty();
    }

    // --- versions and conflicts

    @Test
    void GivenAnAddedAccount_WhenALinkIsSentAndThenItsSessionsEnded_ThenTheVersionIsZeroThenOneThenTwo() {
        int n = 12;
        transactions.inTransaction(() -> repository.add(withPassword(n)));
        long added = storedVersion(n);

        transactions.inTransaction(() -> {
            UserAccount account = repository.get(account(n)).orElseThrow();
            account.issueLink(new LinkPurpose.Recovery(), link(n, 1), hash(n, 1), at(5));
            repository.save(account);
        });
        long linked = storedVersion(n);
        // Voiding the link changes a child row and the generation.
        transactions.inTransaction(() -> {
            UserAccount account = repository.get(account(n)).orElseThrow();
            account.endEverySession(at(6));
            repository.save(account);
        });

        assertThat(List.of(added, linked, storedVersion(n))).containsExactly(0L, 1L, 2L);
    }

    @Test
    void
            GivenAnAccountAddedWithAPassword_WhenItsSessionsAreEndedAndItIsSaved_ThenEachSessionsRevokedIsCommittedAtItsVersion() {
        int n = 27;
        transactions.inTransaction(() -> repository.add(withPassword(n)));

        transactions.inTransaction(() -> {
            UserAccount account = repository.get(account(n)).orElseThrow();
            account.endEverySession(at(6));
            repository.save(account);
        });

        assertThat(events.committed())
                .filteredOn(event -> event instanceof SessionsRevoked revoked
                        && revoked.account().equals(account(n)))
                .extracting(event -> event.eventId().version())
                .containsExactly(Version.of(0), Version.of(1));
    }

    @Test
    void GivenTwoTransactionsOnOneAccount_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        int n = 13;
        transactions.inTransaction(() -> repository.add(withPassword(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    UserAccount mine = repository.get(account(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        UserAccount theirs = repository.get(account(n)).orElseThrow();
                        theirs.changeLanguage(Language.ENGLISH);
                        repository.save(theirs);
                    });
                    mine.acceptTerms(at(5));
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("UserAccount");
                    assertThat(stale.id()).isEqualTo(account(n).value());
                });

        UserAccount stored =
                transactions.inTransaction(() -> repository.get(account(n)).orElseThrow());
        assertThat(stored.version()).isEqualTo(Version.of(1));
        assertThat(stored.language()).isEqualTo(Language.ENGLISH);
        assertThat(stored.termsAcceptedAt()).isEmpty();
    }

    // --- the unique email (BR-45)

    @Test
    void GivenAnAccountHoldingAnAddress_WhenAnotherAccountWithItIsAdded_ThenEmailInUse() {
        int n = 14;
        transactions.inTransaction(() -> repository.add(opened(n, Role.DISPATCHER)));
        // Opened as if no account held the address: another request took it after holderOf read.
        UserAccount late = UserAccount.open(
                account(100 + n), email(n), RoleSet.of(Role.DRIVER), Language.HEBREW, EmailHolder.nobody(email(n)));

        assertRefused(() -> transactions.inTransaction(() -> repository.add(late)), new EmailInUse());
        assertThat(transactions.inTransaction(() -> repository.get(account(100 + n))))
                .isEmpty();
    }

    @Test
    void GivenAnAccountHoldingAnAddress_WhenAnotherConfirmsItAndIsSaved_ThenEmailInUse() {
        int n = 15;
        transactions.inTransaction(() -> {
            repository.add(opened(n, Role.DISPATCHER));
            UserAccount mover = opened(100 + n, Role.DISPATCHER);
            mover.issueLink(new LinkPurpose.EmailChange(email(n), account(100 + n)), link(n, 1), hash(n, 1), at(1));
            repository.add(mover);
        });

        assertRefused(
                () -> transactions.inTransaction(() -> {
                    UserAccount mover = repository.get(account(100 + n)).orElseThrow();
                    mover.confirmEmailByLink(hash(n, 1), EmailHolder.nobody(email(n)), at(2));
                    repository.save(mover);
                }),
                new EmailInUse());
        assertThat(transactions.inTransaction(
                        () -> repository.get(account(100 + n)).orElseThrow().email()))
                .contains(email(100 + n));
    }

    // --- lookups

    @Test
    void GivenActiveDeactivatedAndUnknownAccounts_WhenTheirActiveGenerationIsRead_ThenOnlyTheActiveOneHasOne() {
        int n = 16;
        transactions.inTransaction(() -> {
            repository.add(withPassword(n));
            repository.add(deactivated(100 + n));
        });

        assertThat(transactions.inTransaction(() -> repository.activeGenerationOf(account(n))))
                .contains(AccountGeneration.of(1));
        assertThat(transactions.inTransaction(() -> repository.activeGenerationOf(account(100 + n))))
                .isEmpty();
        assertThat(transactions.inTransaction(() -> repository.activeGenerationOf(account(200 + n))))
                .isEmpty();
    }

    @Test
    void GivenAnAccountWithAnAddress_WhenFoundByItAndAskedWhoHoldsIt_ThenBothAnswerThatAccount() {
        int n = 17;
        transactions.inTransaction(() -> repository.add(linksInEveryStatus(n)));

        UserAccount found =
                transactions.inTransaction(() -> repository.withEmail(email(n)).orElseThrow());

        assertThat(found.id()).isEqualTo(account(n));
        assertThat(found.roles()).isEqualTo(RoleSet.of(Role.DISPATCHER));
        assertThat(found.links()).hasSize(4);
        assertThat(transactions.inTransaction(() -> repository.holderOf(email(n))))
                .isEqualTo(EmailHolder.account(email(n), account(n)));
    }

    @Test
    void GivenAnAddressNoAccountHolds_WhenFoundByItAndAskedWhoHoldsIt_ThenNoAccountAndNobody() {
        int n = 18;

        assertThat(transactions.inTransaction(() -> repository.withEmail(email(n))))
                .isEmpty();
        assertThat(transactions.inTransaction(() -> repository.holderOf(email(n))))
                .isEqualTo(EmailHolder.nobody(email(n)));
    }

    @Test
    void GivenAnAnonymizedAccount_WhenItsFormerAddressIsLookedUp_ThenNoAccountHoldsIt() {
        int n = 19;
        transactions.inTransaction(() -> repository.add(anonymized(n)));

        assertThat(transactions.inTransaction(() -> repository.withEmail(email(n))))
                .isEmpty();
        assertThat(transactions.inTransaction(() -> repository.holderOf(email(n))))
                .isEqualTo(EmailHolder.nobody(email(n)));
    }

    @Test
    void GivenAnAccountsLinks_WhenFoundByAnyLinksHash_ThenThatAccountWholeAndNoneForAnUnknownHash() {
        int n = 20;
        transactions.inTransaction(() -> repository.add(linksInEveryStatus(n)));

        // Link 1 is spent, 2 voided, 3 and 4 open.
        for (int m = 1; m <= 4; m++) {
            int linkNumber = m;
            UserAccount found = transactions.inTransaction(
                    () -> repository.withLink(hash(n, linkNumber)).orElseThrow());
            assertThat(found.id()).isEqualTo(account(n));
            assertThat(found.roles()).isEqualTo(RoleSet.of(Role.DISPATCHER));
        }
        assertThat(transactions.inTransaction(() -> repository.withLink(hash(n, 9))))
                .isEmpty();
    }

    @Test
    void GivenAccountsDeactivatedAroundACutoff_WhenTheDueOnesAreListed_ThenThoseAtOrBeforeItNotAnonymized() {
        int n = 21;
        Instant cutoff = at(100);
        transactions.inTransaction(() -> {
            repository.add(deactivatedAt(n, cutoff.minusSeconds(1)));
            repository.add(deactivatedAt(100 + n, cutoff));
            repository.add(deactivatedAt(200 + n, cutoff.plusSeconds(1)));
            repository.add(withPassword(300 + n));
            repository.add(anonymized(400 + n));
        });
        Set<AccountId> ours =
                Set.of(account(n), account(100 + n), account(200 + n), account(300 + n), account(400 + n));

        List<AccountId> due = transactions.inTransaction(() -> repository.deactivatedBefore(cutoff));

        assertThat(due).filteredOn(ours::contains).containsExactlyInAnyOrder(account(n), account(100 + n));
    }

    @Test
    void GivenAccountsWithOpenEndedAndNoLinks_WhenThoseWithOpenLinksAreListed_ThenEachOneWithAnOpenLinkOnce() {
        int n = 22;
        transactions.inTransaction(() -> {
            repository.add(linksInEveryStatus(n));
            UserAccount spent = opened(100 + n, Role.DISPATCHER);
            spent.issueLink(new LinkPurpose.SetPassword(), link(100 + n, 1), hash(100 + n, 1), at(1));
            spent.setPasswordByLink(hash(100 + n, 1), password(100 + n), at(2));
            repository.add(spent);
            repository.add(withPassword(200 + n));
        });
        Set<AccountId> ours = Set.of(account(n), account(100 + n), account(200 + n));

        List<AccountId> open = transactions.inTransaction(() -> repository.withOpenLinks());

        assertThat(open).filteredOn(ours::contains).containsExactly(account(n));
    }

    @Test
    void GivenLinksSentAroundACutoff_WhenTheAccountsWithOldLinksAreListed_ThenThoseSentAtOrBeforeItEachOnce() {
        int n = 23;
        Instant cutoff = at(100);
        transactions.inTransaction(() -> {
            UserAccount twoOld = opened(n, Role.DISPATCHER);
            twoOld.issueLink(new LinkPurpose.Recovery(), link(n, 1), hash(n, 1), cutoff.minusSeconds(2));
            twoOld.issueLink(new LinkPurpose.Recovery(), link(n, 2), hash(n, 2), cutoff.minusSeconds(1));
            repository.add(twoOld);
            UserAccount atTheCutoff = opened(100 + n, Role.DISPATCHER);
            atTheCutoff.issueLink(new LinkPurpose.Recovery(), link(100 + n, 1), hash(100 + n, 1), cutoff);
            repository.add(atTheCutoff);
            UserAccount after = opened(200 + n, Role.DISPATCHER);
            after.issueLink(new LinkPurpose.Recovery(), link(200 + n, 1), hash(200 + n, 1), cutoff.plusSeconds(1));
            repository.add(after);
        });
        Set<AccountId> ours = Set.of(account(n), account(100 + n), account(200 + n));

        List<AccountId> due = transactions.inTransaction(() -> repository.withLinksSentBefore(cutoff));

        assertThat(due).filteredOn(ours::contains).containsExactlyInAnyOrder(account(n), account(100 + n));
    }

    // --- the active Admins (BR-19, FR-291, FR-389)

    @Test
    void GivenActiveAndDeactivatedAdminsAndADispatcher_WhenTheActiveAdminsAreRead_ThenOnlyTheActiveAdminsCount() {
        int n = 24;
        int before = transactions.inTransaction(
                () -> repository.activeAdminsUnderLock().count());
        transactions.inTransaction(() -> {
            repository.add(opened(n, Role.ADMIN));
            repository.add(opened(100 + n, Role.ADMIN, Role.DRIVER));
            UserAccount gone = opened(200 + n, Role.ADMIN);
            gone.deactivateOnWrittenRequest(at(1));
            repository.add(gone);
            repository.add(opened(300 + n, Role.DISPATCHER));
        });

        int after = transactions.inTransaction(
                () -> repository.activeAdminsUnderLock().count());

        assertThat(after).isEqualTo(before + 2);
    }

    /**
     * The other Admins in the shared database hold row locks too, so the advisory lock is checked
     * by its key, and the row locks on an Admin this test adds.
     */
    @Test
    void GivenATransactionHoldingTheActiveAdmins_WhenAnotherReadsThemOrChangesOne_ThenItCannotTakeTheLock() {
        int n = 26;
        transactions.inTransaction(() -> repository.add(opened(n, Role.ADMIN)));

        transactions.inTransaction(() -> {
            repository.activeAdminsUnderLock();
            assertThatThrownBy(() -> transactions.inANewTransaction(() -> {
                        shortLockTimeout();
                        repository.activeAdminsUnderLock();
                    }))
                    .isInstanceOf(PessimisticLockingFailureException.class);
            transactions.inANewTransaction(
                    () -> assertThat(jdbc.sql("SELECT pg_try_advisory_xact_lock(hashtext('access:active-admins'))")
                                    .query(Boolean.class)
                                    .single())
                            .isFalse());
            assertThatThrownBy(() -> transactions.inANewTransaction(() -> {
                        shortLockTimeout();
                        jdbc.sql("UPDATE access.user_account SET language = 'ENGLISH' WHERE id = ?")
                                .param(account(n).value())
                                .update();
                    }))
                    .hasRootCauseInstanceOf(SQLException.class)
                    .rootCause()
                    .extracting(cause -> ((SQLException) cause).getSQLState())
                    .isEqualTo(LOCK_NOT_AVAILABLE);
        });

        transactions.inANewTransaction(() -> {
            shortLockTimeout();
            repository.activeAdminsUnderLock();
        });
    }

    /** A lock taken in a transaction of the call's own would be gone before the caller acts. */
    @Test
    void GivenNoTransaction_WhenTheActiveAdminsAreReadUnderLock_ThenTheCallIsRefused() {
        assertThatThrownBy(() -> repository.activeAdminsUnderLock())
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void GivenAccountsWithAndWithoutTheAdminRole_WhenTheAdminRecipientsAreRead_ThenEveryActiveAdminOnce() {
        int n = 25;
        transactions.inTransaction(() -> {
            repository.add(opened(n, Role.ADMIN, Role.DISPATCHER));
            UserAccount gone = opened(100 + n, Role.ADMIN);
            gone.deactivateOnWrittenRequest(at(1));
            repository.add(gone);
            repository.add(opened(200 + n, Role.DISPATCHER));
            repository.add(opened(300 + n, Role.DRIVER));
        });

        List<AdminNotice.Recipient> recipients = transactions.inTransaction(() -> {
            UserAccount promoted = repository.get(account(300 + n)).orElseThrow();
            promoted.changeRoles(RoleSet.of(Role.ADMIN), NO_ADMINS, OTHER_ADMIN, at(2));
            repository.save(promoted);
            return repository.activeAdminRecipients();
        });

        Set<EmailAddress> ours = Set.of(email(n), email(100 + n), email(200 + n), email(300 + n));
        assertThat(recipients)
                .filteredOn(recipient -> ours.contains(recipient.email()))
                .containsExactlyInAnyOrder(
                        new AdminNotice.Recipient(email(n), Language.HEBREW),
                        new AdminNotice.Recipient(email(300 + n), Language.HEBREW));
    }

    // --- the load check (ADR-127 decision 1)

    static Stream<Arguments> damagedRows() {
        String emailHeld = "an anonymized account holds no email, and every other account holds one";
        String nothingLeft = "an anonymized account holds no password and no link";
        String setByAnother = "an email another account set belongs to an account that holds it and no Admin role";
        return Stream.of(
                Arguments.of(
                        "an active account with no email",
                        1_001,
                        Base.ACTIVE,
                        (Damage) rows -> rows.setOnAccount("email = NULL"),
                        emailHeld),
                Arguments.of(
                        "an anonymized account still holding a link",
                        1_004,
                        Base.ANONYMIZED,
                        (Damage) Rows::insertVoidedRecoveryLink,
                        nothingLeft),
                Arguments.of(
                        "an Admin whose email another account set",
                        1_005,
                        Base.EMAIL_SET_BY_ANOTHER,
                        (Damage) Rows::giveTheAdminRole,
                        setByAnother),
                Arguments.of(
                        "an account recording that its own link set its email",
                        1_007,
                        Base.EMAIL_SET_BY_ANOTHER,
                        (Damage) rows -> rows.setOnAccount("email_set_by = id"),
                        "the account that set an email is another account"),
                Arguments.of(
                        "an account anonymized before it was deactivated",
                        1_008,
                        Base.ANONYMIZED,
                        (Damage) rows -> rows.setOnAccount("anonymized_at = deactivated_at - interval '1 day'"),
                        "an account is anonymized after it is deactivated"),
                Arguments.of(
                        "two open links of one kind",
                        1_011,
                        Base.TWO_RECOVERY_LINKS,
                        (Damage) rows -> rows.setOnLink(1, "status = 'OPEN'"),
                        "a newer link voids older open links of its kind"),
                Arguments.of(
                        "a link that expires when it is sent",
                        1_012,
                        Base.TWO_RECOVERY_LINKS,
                        (Damage) rows -> rows.setOnLink(2, "expires_at = issued_at"),
                        "a link expires after it is sent"),
                Arguments.of(
                        "links whose positions start past the first",
                        1_017,
                        Base.TWO_RECOVERY_LINKS,
                        (Damage) Rows::shiftLinkPositions,
                        StoredLists.GAP));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredAccountBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Base base, Damage damage, String detail) {
        transactions.inTransaction(() -> repository.add(base.build(n)));

        damage.apply(new Rows(jdbc, n));

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(account(n))))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("UserAccount");
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("two links with one ID", 1_009, Base.TWO_RECOVERY_LINKS, (Damage)
                        rows -> rows.setOnLink(2, "id = ?", link(1_009, 1).value())),
                Arguments.of("a second account with the address", 1_013, Base.TWO_RECOVERY_LINKS, (Damage)
                        rows -> rows.setOnAccount("email = ?", email(1_113).value())),
                Arguments.of("a deactivated account with no deactivation time", 1_014, Base.TWO_RECOVERY_LINKS, (Damage)
                        rows -> rows.setOnAccount("state = 'DEACTIVATED'")),
                Arguments.of("an email-change link with no address", 1_015, Base.TWO_RECOVERY_LINKS, (Damage)
                        rows -> rows.setOnLink(1, "kind = 'EMAIL_CHANGE'")),
                Arguments.of("two links with one token hash", 1_010, Base.TWO_RECOVERY_LINKS, (Damage) rows ->
                        rows.setOnLink(2, "token_hash = ?", hash(1_010, 1).value())),
                Arguments.of("a link with another account's token hash", 1_016, Base.TWO_RECOVERY_LINKS, (Damage)
                        rows -> rows.setOnLink(
                                2, "token_hash = ?", hash(1_116, 1).value())),
                Arguments.of("an anonymized account still holding its email", 1_002, Base.ANONYMIZED, (Damage)
                        rows -> rows.setOnAccount("email = ?", email(1_002).value())),
                Arguments.of("an anonymized account still holding a password", 1_003, Base.ANONYMIZED, (Damage)
                        rows -> rows.setOnAccount("password_hash = 'stored-hash'")),
                Arguments.of("an anonymized account still recording who set its email", 1_006, Base.ANONYMIZED, (Damage)
                        rows -> rows.setOnAccount(
                                "email_set_by = ?, email_set_at = anonymized_at", OTHER_ADMIN.value())));
    }

    /** Account {@code 100 + n}, beside every base, holds the address and the link hash the cases take. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredAccount_WhenARowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Base base, Damage damage) {
        transactions.inTransaction(() -> {
            repository.add(base.build(n));
            UserAccount other = opened(100 + n, Role.DRIVER);
            other.issueLink(new LinkPurpose.Recovery(), link(100 + n, 1), hash(100 + n, 1), at(1));
            repository.add(other);
        });
        Rows rows = new Rows(jdbc, n);

        assertThatThrownBy(() -> damage.apply(rows)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(account(n)))).isPresent();
    }

    /** The valid states the damage cases start from, each a Dispatcher. */
    enum Base {
        ACTIVE,
        ANONYMIZED,
        EMAIL_SET_BY_ANOTHER,
        /** Recovery link 1, voided by recovery link 2, which is open. */
        TWO_RECOVERY_LINKS;

        UserAccount build(int n) {
            return switch (this) {
                case ACTIVE -> withPassword(n);
                case ANONYMIZED -> anonymized(n);
                case EMAIL_SET_BY_ANOTHER -> emailSetByAnother(n);
                case TWO_RECOVERY_LINKS -> {
                    UserAccount account = withPassword(n);
                    account.issueLink(new LinkPurpose.Recovery(), link(n, 1), hash(n, 1), at(1));
                    account.issueLink(new LinkPurpose.Recovery(), link(n, 2), hash(n, 2), at(2));
                    yield account;
                }
            };
        }
    }

    /** One damage done with SQL to a stored account's rows. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /** The SQL the damage cases run on account {@code n}'s rows. */
    record Rows(JdbcClient jdbc, int n) {

        /** Sets columns of the account; the assignments are the test's own constant SQL. */
        void setOnAccount(String assignments, Object... values) {
            update(
                    "UPDATE access.user_account SET " + assignments + " WHERE id = ?",
                    values,
                    account(n).value());
        }

        /** Sets columns of link {@code m}; the assignments are the test's own constant SQL. */
        void setOnLink(int m, String assignments, Object... values) {
            update(
                    "UPDATE access.credential_link SET " + assignments + " WHERE id = ?",
                    values,
                    link(n, m).value());
        }

        /** Moves every link one position on, so no link holds the first. */
        void shiftLinkPositions() {
            jdbc.sql("UPDATE access.credential_link SET position = position + 1 WHERE user_account_id = ?")
                    .param(account(n).value())
                    .update();
        }

        void giveTheAdminRole() {
            jdbc.sql("INSERT INTO access.user_account_role (user_account_id, role) VALUES (?, 'ADMIN')")
                    .param(account(n).value())
                    .update();
        }

        void insertVoidedRecoveryLink() {
            jdbc.sql("""
                            INSERT INTO access.credential_link
                                (id, user_account_id, position, kind, token_hash, issued_at, expires_at, status)
                            VALUES (?, ?, 0, 'RECOVERY', 'damage', ?, ?, 'VOIDED')
                            """)
                    .params(
                            link(n, 9).value(),
                            account(n).value(),
                            at(1).atOffset(ZoneOffset.UTC),
                            at(61).atOffset(ZoneOffset.UTC))
                    .update();
        }

        private void update(String sql, Object[] values, UUID key) {
            List<Object> params = new ArrayList<>(List.of(values));
            params.add(key);
            jdbc.sql(sql).params(params).update();
        }
    }

    // --- states, reached through the domain's commands

    /** An account opened by an Admin with the roles, at {@link #T}, with no password. */
    private static UserAccount opened(int n, Role... roles) {
        return UserAccount.open(account(n), email(n), RoleSet.of(roles), Language.HEBREW, EmailHolder.nobody(email(n)));
    }

    /** A Dispatcher whose password was changed once, so at generation 1. */
    private static UserAccount withPassword(int n) {
        UserAccount account = opened(n, Role.DISPATCHER);
        account.changePassword(password(n), at(1));
        return account;
    }

    /** A Dispatcher whose address the other Admin's email-change link set. */
    private static UserAccount emailSetByAnother(int n) {
        UserAccount account = withPassword(n);
        account.issueLink(new LinkPurpose.EmailChange(newEmail(n), OTHER_ADMIN), link(n, 1), hash(n, 1), at(2));
        account.confirmEmailByLink(hash(n, 1), EmailHolder.nobody(newEmail(n)), at(3));
        return account;
    }

    /** A spent set-password link, a voided and an open recovery link, and an open email change. */
    private static UserAccount linksInEveryStatus(int n) {
        UserAccount account = opened(n, Role.DISPATCHER);
        account.issueLink(new LinkPurpose.SetPassword(), link(n, 1), hash(n, 1), at(1));
        account.setPasswordByLink(hash(n, 1), password(n), at(2));
        account.issueLink(new LinkPurpose.Recovery(), link(n, 2), hash(n, 2), at(3));
        account.issueLink(new LinkPurpose.Recovery(), link(n, 3), hash(n, 3), at(4));
        account.issueLink(new LinkPurpose.EmailChange(newEmail(n), account(n)), link(n, 4), hash(n, 4), at(5));
        return account;
    }

    private static UserAccount deactivated(int n) {
        return deactivatedAt(n, at(10));
    }

    private static UserAccount deactivatedAt(int n, Instant at) {
        UserAccount account = withPassword(n);
        account.deactivate(NO_ADMINS, at);
        return account;
    }

    /** Deactivated with a link sent, and anonymized a year and a day later. */
    private static UserAccount anonymized(int n) {
        UserAccount account = withPassword(n);
        account.issueLink(new LinkPurpose.Recovery(), link(n, 1), hash(n, 1), at(2));
        account.deactivate(NO_ADMINS, at(10));
        account.anonymize(at(10).plus(A_YEAR_AND_A_DAY));
        return account;
    }

    // --- keys, values and stored values

    private static AccountId account(int n) {
        return AccountId.of(new UUID(KEY_PREFIX, n));
    }

    private static CredentialLinkId link(int n, int m) {
        return CredentialLinkId.of(new UUID(KEY_PREFIX + 1, 100L * n + m));
    }

    private static TokenHash hash(int n, int m) {
        return new TokenHash("link-" + n + "-" + m);
    }

    private static EmailAddress email(int n) {
        return EmailAddress.parse("user" + n + "@account-it.example");
    }

    private static EmailAddress newEmail(int n) {
        return EmailAddress.parse("moved" + n + "@account-it.example");
    }

    private static PasswordHash password(int n) {
        return new PasswordHash("hash-" + n);
    }

    private static Instant at(int minutesLater) {
        return T.plus(Duration.ofMinutes(minutesLater));
    }

    private void shortLockTimeout() {
        jdbc.sql("SET LOCAL lock_timeout = '200ms'").update();
    }

    private long storedVersion(int n) {
        return jdbc.sql("SELECT version FROM access.user_account WHERE id = ?")
                .param(account(n).value())
                .query(Long.class)
                .single();
    }
}
