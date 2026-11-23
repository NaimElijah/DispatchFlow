package com.dispatchflow.access.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.access.application.port.out.CompanyProfileRepository;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.companyprofile.CompanyName;
import com.dispatchflow.access.domain.companyprofile.CompanyProfile;
import com.dispatchflow.access.domain.companyprofile.CompanyProfileSnapshot;
import com.dispatchflow.access.domain.companyprofile.DefaultServiceTime;
import com.dispatchflow.access.domain.companyprofile.DefaultWindow;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link CompanyProfileRepository} against a real PostgreSQL (ADR-126, ADR-127): the one row of the
 * company's profile, its version, the first save another Admin wins, and the stored values its
 * converters and instantiator refuse.
 *
 * <p>There is one row for the whole database, and only this class writes it, so each test starts
 * with none.
 */
@IntegrationTest
class CompanyProfilePersistenceIT {

    private static final CompanyName NAME = new CompanyName("מהיר משלוחים");
    private static final DefaultWindow NINE_TO_FIVE = new DefaultWindow(LocalTime.of(9, 0), LocalTime.of(17, 0));
    private static final DefaultWindow EIGHT_TO_NOON = new DefaultWindow(LocalTime.of(8, 0), LocalTime.of(12, 0));

    @Autowired
    private CompanyProfileRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void startWithNoProfile() {
        jdbc.sql("DELETE FROM access.company_profile").update();
    }

    @Test
    void GivenNoAdminSavedTheProfile_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get())).isEmpty();
    }

    /** The two states the domain's restore round trip covers, reached through commands. */
    static Stream<Optional<DefaultServiceTime>> serviceTimes() {
        return Stream.of(Optional.empty(), Optional.of(new DefaultServiceTime(240)));
    }

    @ParameterizedTest
    @MethodSource("serviceTimes")
    void GivenAProfile_WhenAddedAndLoadedInAnotherTransaction_ThenEveryValueComesBackUnderTheConstantKey(
            Optional<DefaultServiceTime> serviceTime) {
        CompanyProfileSnapshot added = transactions.inTransaction(() -> {
            CompanyProfile profile = CompanyProfile.create(NAME, Language.HEBREW, NINE_TO_FIVE);
            serviceTime.ifPresent(profile::changeDefaultServiceTime);
            repository.add(profile);
            return profile.snapshot();
        });

        CompanyProfile loaded =
                transactions.inTransaction(() -> repository.get().orElseThrow());

        assertThat(loaded.snapshot()).isEqualTo(added);
        assertThat(loaded.snapshot())
                .isEqualTo(new CompanyProfileSnapshot(Version.of(0), NAME, Language.HEBREW, NINE_TO_FIVE, serviceTime));
        assertThat(jdbc.sql("SELECT id FROM access.company_profile")
                        .query(UUID.class)
                        .single())
                .isEqualTo(CompanyProfile.KEY);
    }

    @Test
    void GivenAnAddedProfile_WhenChangedAndSavedTwice_ThenTheVersionIsZeroThenOneThenTwo() {
        transactions.inTransaction(() -> repository.add(CompanyProfile.create(NAME, Language.HEBREW, NINE_TO_FIVE)));
        long added = storedVersion();

        transactions.inTransaction(() -> {
            CompanyProfile profile = repository.get().orElseThrow();
            profile.changeDefaultWindow(EIGHT_TO_NOON);
            repository.save(profile);
        });
        long changed = storedVersion();
        transactions.inTransaction(() -> repository.save(repository.get().orElseThrow()));

        assertThat(List.of(added, changed, storedVersion())).containsExactly(0L, 1L, 2L);
    }

    @Test
    void GivenTwoAdminsChangingTheProfile_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        transactions.inTransaction(() -> repository.add(CompanyProfile.create(NAME, Language.HEBREW, NINE_TO_FIVE)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    CompanyProfile mine = repository.get().orElseThrow();
                    transactions.inANewTransaction(() -> {
                        CompanyProfile theirs = repository.get().orElseThrow();
                        theirs.changeLanguage(Language.ENGLISH);
                        repository.save(theirs);
                    });
                    mine.rename(new CompanyName("Fast Deliveries"));
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("CompanyProfile");
                    assertThat(stale.id()).isEqualTo(CompanyProfile.KEY);
                });

        assertThat(transactions.inTransaction(
                        () -> repository.get().orElseThrow().snapshot()))
                .isEqualTo(new CompanyProfileSnapshot(
                        Version.of(1), NAME, Language.ENGLISH, NINE_TO_FIVE, Optional.empty()));
    }

    @Test
    void GivenAnotherAdminStoredTheFirstProfile_WhenItIsAdded_ThenStaleVersionAndTheFirstStands() {
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    CompanyProfile mine = CompanyProfile.create(NAME, Language.HEBREW, NINE_TO_FIVE);
                    transactions.inANewTransaction(
                            () -> repository.add(CompanyProfile.create(NAME, Language.ENGLISH, EIGHT_TO_NOON)));
                    repository.add(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("CompanyProfile");
                    assertThat(stale.id()).isEqualTo(CompanyProfile.KEY);
                });

        assertThat(transactions.inTransaction(
                        () -> repository.get().orElseThrow().snapshot()))
                .isEqualTo(new CompanyProfileSnapshot(
                        Version.of(0), NAME, Language.ENGLISH, EIGHT_TO_NOON, Optional.empty()));
    }

    /** Each damage done to a stored value, and the value that refuses it when the profile loads. */
    static Stream<Arguments> refusedValues() {
        return Stream.of(
                Arguments.of("name = ' '", "CompanyName"),
                Arguments.of("default_window_opens = default_window_closes", "DefaultWindow"),
                Arguments.of("default_service_time = 0", "DefaultServiceTime"));
    }

    /**
     * Hibernate wraps what a converter throws in its own exception, which Spring translates, so a
     * converter's refusal is found among the causes; the instantiator's arrives as itself.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedValues")
    void GivenAStoredValueItsTypeNowRefuses_WhenTheProfileLoads_ThenStoredStateInvalidNamesTheValue(
            String damage, String refusedBy) {
        transactions.inTransaction(() -> repository.add(CompanyProfile.create(NAME, Language.HEBREW, NINE_TO_FIVE)));
        // The damage is the test's own constant SQL.
        jdbc.sql("UPDATE access.company_profile SET " + damage).update();

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get()))
                .satisfies(thrown -> assertThat(storedStateInvalidIn(thrown))
                        .hasValueSatisfying(
                                invalid -> assertThat(invalid.subject()).isEqualTo(refusedBy)));
    }

    @Test
    void GivenAnotherKey_WhenARowIsInsertedUnderIt_ThenTheDatabaseRefusesASecondProfile() {
        assertThatThrownBy(() -> jdbc.sql("""
                                INSERT INTO access.company_profile
                                    (id, version, name, language, default_window_opens, default_window_closes)
                                VALUES (?, 0, 'Other', 'HEBREW', '09:00', '17:00')
                                """)
                        .param(UUID.fromString("0190a5e2-636f-7000-8000-000000000001"))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Optional<StoredStateInvalid> storedStateInvalidIn(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof StoredStateInvalid invalid) {
                return Optional.of(invalid);
            }
        }
        return Optional.empty();
    }

    private long storedVersion() {
        return jdbc.sql("SELECT version FROM access.company_profile")
                .query(Long.class)
                .single();
    }
}
