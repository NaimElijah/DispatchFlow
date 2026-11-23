package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHashes;
import com.dispatchflow.messaging.domain.OptOutRecord;
import com.dispatchflow.messaging.domain.PhoneHash;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the phones' opt-out history in {@code messaging.opt_out}. */
@Component
@Transactional
class JpaOptOutList implements OptOutList {

    private final StoredOptOuts optOuts;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param optOuts the Spring Data interface
     * @param entityManager the transaction-bound entity manager, which inserts each new entry
     */
    JpaOptOutList(StoredOptOuts optOuts, EntityManager entityManager) {
        this.optOuts = Objects.requireNonNull(optOuts, "optOuts");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public List<OptOutRecord> historyOf(PhoneHashes phone) {
        List<PhoneHash> hashes = Stream.concat(Stream.of(phone.current()), phone.older().stream())
                .toList();
        return optOuts.findByPhoneInOrderByAppended(hashes).stream()
                .map(StoredOptOut::toRecord)
                .toList();
    }

    @Override
    public void append(OptOutRecord entry) {
        entityManager.persist(new StoredOptOut(UUID.randomUUID(), entry));
    }
}
