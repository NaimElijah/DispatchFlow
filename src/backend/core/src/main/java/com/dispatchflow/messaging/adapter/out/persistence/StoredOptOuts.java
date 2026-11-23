package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.PhoneHash;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaOptOutList}. It declares no {@code save}: Spring Data's
 * would {@code merge} a row whose key is already set, which reads before it writes, so the adapter
 * persists each new row itself.
 */
interface StoredOptOuts extends Repository<StoredOptOut, UUID> {

    /**
     * The entries stored under any of a phone's hashes.
     *
     * @param phones the hashes
     * @return the entries, in the order they were appended
     */
    List<StoredOptOut> findByPhoneInOrderByAppended(Collection<PhoneHash> phones);
}
