package com.dispatchflow.messaging.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsRepository;
import com.dispatchflow.messaging.domain.DispatchPhone;
import com.dispatchflow.messaging.domain.Switch;
import com.dispatchflow.messaging.domain.settings.MessagingSettings;
import com.dispatchflow.messaging.domain.settings.MessagingSettingsSnapshot;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link MessagingSettingsRepository} against a real PostgreSQL (ADR-126, ADR-127): the one row of
 * the company's settings, its version, and the first save another Admin wins.
 *
 * <p>There is one row for the whole database, and only this class writes it, so each test starts
 * with none.
 */
@IntegrationTest
class MessagingSettingsPersistenceIT {

    private static final DispatchPhone DISPATCH = DispatchPhone.of(PhoneNumber.ofStored("+97235551234"));
    private static final DispatchPhone MOBILE = DispatchPhone.of(PhoneNumber.ofStored("+972523456789"));

    @Autowired
    private MessagingSettingsRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void startWithNoSettings() {
        jdbc.sql("DELETE FROM messaging.messaging_settings").update();
    }

    @Test
    void GivenNoAdminSetTheSettings_WhenTheyAreLoaded_ThenThereAreNone() {
        assertThat(transactions.inTransaction(() -> repository.get())).isEmpty();
    }

    @Test
    void GivenChangedSettings_WhenAddedAndLoadedInAnotherTransaction_ThenEveryValueComesBackUnderTheConstantKey() {
        MessagingSettingsSnapshot added = transactions.inTransaction(() -> {
            MessagingSettings settings = MessagingSettings.create(DISPATCH);
            settings.changeMessages(Switch.ON);
            settings.changeRatingQuestion(Switch.ON);
            settings.changeDispatchPhone(MOBILE);
            repository.add(settings);
            return settings.snapshot();
        });

        MessagingSettings loaded =
                transactions.inTransaction(() -> repository.get().orElseThrow());

        assertThat(loaded.snapshot()).isEqualTo(added);
        assertThat(loaded.snapshot())
                .isEqualTo(new MessagingSettingsSnapshot(Version.of(0), Switch.ON, Switch.ON, MOBILE));
        assertThat(jdbc.sql("SELECT id FROM messaging.messaging_settings")
                        .query(UUID.class)
                        .single())
                .isEqualTo(MessagingSettings.KEY);
    }

    @Test
    void GivenAddedSettings_WhenChangedAndSavedTwice_ThenTheVersionIsZeroThenOneThenTwo() {
        transactions.inTransaction(() -> repository.add(MessagingSettings.create(DISPATCH)));
        long added = storedVersion();

        transactions.inTransaction(() -> {
            MessagingSettings settings = repository.get().orElseThrow();
            settings.changeMessages(Switch.ON);
            repository.save(settings);
        });
        long changed = storedVersion();
        transactions.inTransaction(() -> repository.save(repository.get().orElseThrow()));

        assertThat(List.of(added, changed, storedVersion())).containsExactly(0L, 1L, 2L);
    }

    @Test
    void GivenTwoAdminsChangingTheSettings_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        transactions.inTransaction(() -> repository.add(MessagingSettings.create(DISPATCH)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    MessagingSettings mine = repository.get().orElseThrow();
                    transactions.inANewTransaction(() -> {
                        MessagingSettings theirs = repository.get().orElseThrow();
                        theirs.changeMessages(Switch.ON);
                        repository.save(theirs);
                    });
                    mine.changeRatingQuestion(Switch.ON);
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("MessagingSettings");
                    assertThat(stale.id()).isEqualTo(MessagingSettings.KEY);
                });

        assertThat(transactions.inTransaction(
                        () -> repository.get().orElseThrow().snapshot()))
                .isEqualTo(new MessagingSettingsSnapshot(Version.of(1), Switch.ON, Switch.OFF, DISPATCH));
    }

    @Test
    void GivenAnotherAdminStoredTheFirstSettings_WhenTheyAreAdded_ThenStaleVersionAndTheFirstStand() {
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    MessagingSettings mine = MessagingSettings.create(DISPATCH);
                    transactions.inANewTransaction(() -> repository.add(MessagingSettings.create(MOBILE)));
                    repository.add(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("MessagingSettings");
                    assertThat(stale.id()).isEqualTo(MessagingSettings.KEY);
                });

        assertThat(transactions.inTransaction(
                        () -> repository.get().orElseThrow().snapshot()))
                .isEqualTo(new MessagingSettingsSnapshot(Version.of(0), Switch.OFF, Switch.OFF, MOBILE));
    }

    private long storedVersion() {
        return jdbc.sql("SELECT version FROM messaging.messaging_settings")
                .query(Long.class)
                .single();
    }
}
