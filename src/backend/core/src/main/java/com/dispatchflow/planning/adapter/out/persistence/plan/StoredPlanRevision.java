package com.dispatchflow.planning.adapter.out.persistence.plan;

import com.dispatchflow.planning.domain.revision.PlanRevision;
import com.dispatchflow.planning.domain.revision.RevisionRoute;
import com.dispatchflow.planning.events.RevisionCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/**
 * One published revision of a plan, appended once when the plan is saved and never changed or loaded
 * with the plan (DL §4.4, ADR-059, ADR-126). It is keyed by its plan and its number, so a revision is
 * stored once. Its routes are rows of their own, and their stops one table keyed by the route's
 * position and the stop's own (ADR-127 decision 3).
 *
 * <p>Nothing reads a revision back yet: late reports judged against the revision in force come with
 * the queries that need them (BR-110).
 */
@Entity
@Immutable
@Table(schema = "planning", name = "plan_revision")
@IdClass(StoredPlanRevision.Key.class)
class StoredPlanRevision {

    @Id
    @Column(name = "plan_id")
    private UUID plan;

    @Id
    private int revision;

    private Instant effectiveAt;

    @Enumerated(EnumType.STRING)
    private RevisionCause cause;

    @Column(name = "proposal_id")
    private @Nullable ProposalId proposal;

    @Column(name = "made_by")
    private @Nullable AccountId by;

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "plan_revision_route",
            joinColumns = {@JoinColumn(name = "plan_id"), @JoinColumn(name = "revision")})
    @OrderColumn(name = "position", nullable = false)
    private List<StoredRevisionRoute> routes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "plan_revision_stop",
            joinColumns = {@JoinColumn(name = "plan_id"), @JoinColumn(name = "revision")})
    @OrderColumn(name = "position", nullable = false)
    private List<StoredRevisionStop> stops = new ArrayList<>();

    /** For Hibernate. */
    protected StoredPlanRevision() {}

    /**
     * Stores a revision of a plan, with its routes and their stops.
     *
     * @param plan the plan
     * @param revision the revision its last change made
     */
    StoredPlanRevision(PlanId plan, PlanRevision revision) {
        this.plan = plan.value();
        this.revision = revision.number().value();
        this.effectiveAt = revision.effectiveAt();
        this.cause = revision.cause();
        this.proposal = revision.proposal().orElse(null);
        this.by = revision.by().orElse(null);
        List<RevisionRoute> laid = revision.routes();
        for (int routePosition = 0; routePosition < laid.size(); routePosition++) {
            RevisionRoute route = laid.get(routePosition);
            routes.add(new StoredRevisionRoute(route));
            for (int stopPosition = 0; stopPosition < route.stops().size(); stopPosition++) {
                stops.add(new StoredRevisionStop(
                        routePosition, stopPosition, route.stops().get(stopPosition)));
            }
        }
    }

    /**
     * A revision's key: its plan's UUID and its number, as Jakarta Persistence needs it for a key of
     * two columns.
     */
    public static final class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private UUID plan;

        private int revision;

        /** For Hibernate, which sets the fields from the stored row. */
        public Key() {}

        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof Key key && key.revision == revision && Objects.equals(key.plan, plan);
        }

        @Override
        public int hashCode() {
            return Objects.hash(plan, revision);
        }
    }
}
