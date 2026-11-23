package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.application.port.out.KeptPosition;
import com.dispatchflow.liveops.application.port.out.PositionLog;
import com.dispatchflow.shared.RouteId;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the positions routes kept in {@code liveops.kept_position}, one row each. */
@Component
@Transactional
class JpaPositionLog implements PositionLog {

    private final StoredPositions positions;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param positions the Spring Data interface
     * @param entityManager the transaction-bound entity manager, which inserts each new position
     */
    JpaPositionLog(StoredPositions positions, EntityManager entityManager) {
        this.positions = Objects.requireNonNull(positions, "positions");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public void append(List<KeptPosition> kept) {
        kept.forEach(position -> entityManager.persist(new StoredPosition(UUID.randomUUID(), position)));
    }

    @Override
    public List<KeptPosition> lastTwo(RouteId route) {
        List<KeptPosition> latestFirst = positions.findFirst2ByRouteOrderByRecordedAtDescReceivedAtDesc(route).stream()
                .map(StoredPosition::toRecord)
                .toList();
        return latestFirst.reversed();
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return positions.deleteReceivedBy(cutoff);
    }
}
