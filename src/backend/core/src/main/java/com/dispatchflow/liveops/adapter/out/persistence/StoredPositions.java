package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaPositionLog}. It declares no {@code save}: Spring
 * Data's would {@code merge} a row whose key is already set, which reads before it writes, so the
 * adapter persists each new row itself.
 */
interface StoredPositions extends Repository<StoredPosition, UUID> {

    /**
     * A route's two latest positions by recorded time, the later receipt first among equal times.
     *
     * @param route the route
     * @return at most two positions, the latest first
     */
    List<StoredPosition> findFirst2ByRouteOrderByRecordedAtDescReceivedAtDesc(RouteId route);

    /**
     * Deletes every position received at or before a cutoff.
     *
     * @param cutoff the latest receipt time that is due
     * @return how many positions were deleted
     */
    @Modifying
    @Query(value = "DELETE FROM liveops.kept_position WHERE received_at <= :cutoff", nativeQuery = true)
    int deleteReceivedBy(Instant cutoff);
}
