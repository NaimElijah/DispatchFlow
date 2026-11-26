package com.dispatchflow.messaging.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.DatabaseOwner;
import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHashes;
import com.dispatchflow.messaging.domain.OptOutRecord;
import com.dispatchflow.messaging.domain.OptOutRecord.Change;
import com.dispatchflow.messaging.domain.PhoneHash;
import com.dispatchflow.shared.AccountId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link OptOutList} against a real PostgreSQL (ADR-127 decision 6): a phone's history comes back in
 * the order it was appended, under every key version, rebuilt through {@code OptOutRecord.ofStored}.
 *
 * <p>The database is shared and never cleaned, so each test hashes its own phones.
 */
@IntegrationTest
class OptOutListPersistenceIT {

    private static final Instant NOON = Instant.parse("2030-01-01T10:00:00Z");
    private static final AccountId DISPATCHER = AccountId.of(new UUID(0x0190a5e2_6f70_7400L, 1));

    @Autowired
    private OptOutList optOuts;

    @Autowired
    private Transactions transactions;

    @Autowired
    @DatabaseOwner
    private JdbcClient owner;

    @Test
    void GivenAPhoneWithNoEntry_WhenItsHistoryIsRead_ThenItIsEmpty() {
        assertThat(transactions.inTransaction(() -> optOuts.historyOf(hashes(hash("1")))))
                .isEmpty();
    }

    @Test
    void GivenEntriesAppendedOutOfTimeOrder_WhenTheHistoryIsRead_ThenItComesInTheOrderAppendedAndWhole() {
        PhoneHash phone = hash("2");
        OptOutRecord byReply = OptOutRecord.ofStored(phone, Change.OPTED_OUT, NOON, Optional.empty());
        OptOutRecord removedEarlier =
                OptOutRecord.ofStored(phone, Change.OPTED_IN, NOON.minusSeconds(60), Optional.of(DISPATCHER));
        OptOutRecord recordedEarliest =
                OptOutRecord.ofStored(phone, Change.OPTED_OUT, NOON.minusSeconds(120), Optional.of(DISPATCHER));
        transactions.inTransaction(() -> optOuts.append(byReply));
        transactions.inTransaction(() -> {
            optOuts.append(removedEarlier);
            optOuts.append(recordedEarliest);
        });

        List<OptOutRecord> history = transactions.inTransaction(() -> optOuts.historyOf(hashes(phone)));

        assertThat(history).containsExactly(byReply, removedEarlier, recordedEarliest);
    }

    @Test
    void GivenEntriesUnderTwoKeyVersionsAndAnotherPhones_WhenTheHistoryIsRead_ThenItHasEveryKeysEntriesAndNoOthers() {
        PhoneHash current = hash("3-current");
        PhoneHash older = hash("3-older");
        OptOutRecord underOlder = OptOutRecord.ofStored(older, Change.OPTED_OUT, NOON, Optional.empty());
        OptOutRecord underCurrent =
                OptOutRecord.ofStored(current, Change.OPTED_IN, NOON.plusSeconds(60), Optional.of(DISPATCHER));
        transactions.inTransaction(() -> {
            optOuts.append(underOlder);
            optOuts.append(OptOutRecord.ofStored(hash("3-other"), Change.OPTED_OUT, NOON, Optional.empty()));
            optOuts.append(underCurrent);
        });

        List<OptOutRecord> history =
                transactions.inTransaction(() -> optOuts.historyOf(new PhoneHashes(current, List.of(older))));

        assertThat(history).containsExactly(underOlder, underCurrent);
    }

    @Test
    void GivenAStoredOptIn_WhenItsDispatcherIsRemoved_ThenTheDatabaseRefusesTheWrite() {
        PhoneHash phone = hash("4");
        transactions.inTransaction(
                () -> optOuts.append(OptOutRecord.ofStored(phone, Change.OPTED_IN, NOON, Optional.of(DISPATCHER))));

        assertThatThrownBy(() -> owner.sql("UPDATE messaging.opt_out SET dispatcher = NULL WHERE phone_hash = ?")
                        .param(phone.value())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static PhoneHash hash(String phone) {
        return new PhoneHash("opt-out-list-it-" + phone);
    }

    private static PhoneHashes hashes(PhoneHash current) {
        return new PhoneHashes(current, List.of());
    }
}
