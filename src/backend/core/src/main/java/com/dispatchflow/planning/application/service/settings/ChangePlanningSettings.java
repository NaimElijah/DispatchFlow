package com.dispatchflow.planning.application.service.settings;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.StepUp;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.settings.PlanningSettingsRepository;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.settings.Buffer;
import com.dispatchflow.planning.domain.settings.DepartureAllowance;
import com.dispatchflow.planning.domain.settings.FixedVehicleCost;
import com.dispatchflow.planning.domain.settings.TravelBuckets;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;

/**
 * Changes the planning settings, all in one place, from the Admin's settings page (FR-183, FR-19,
 * FR-21, FR-67, FR-226, FR-227, FR-321, FR-322, FR-408, placement P7). Each value checked itself when
 * the web adapter built it, so the settings refuse nothing more. A change applies to the next solve,
 * evaluation or re-optimization; a plan already solved keeps the values it was solved with (BR-37,
 * ADR-052).
 *
 * <p>The change is Admin-only, so it first asks Access's {@link StepUp} whether the acting session
 * gave its password within the step-up window, before it reads the settings (NFR-214, ADR-068,
 * ADR-104). The step-up arrives in M6.
 *
 * <p>{@code PlanningSettings} has no default solver of each kind yet, which ADR-004 puts there: a
 * domain gap that M4 closes (placement P3). Until M4 adds the field, the {@link Command} has no
 * default solver to change, and a solve with no solver runs OR-Tools, the default that ADR-004 and
 * FR-20 name. M4 adds the daily and the re-optimization defaults to the command with the field.
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M4.
 */
public final class ChangePlanningSettings {

    private final PlanningSettingsRepository settings;
    private final StepUp stepUp;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param settings where the settings are kept
     * @param stepUp Access's recent-password check (NFR-214)
     * @param clock the time, for the audit record
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public ChangePlanningSettings(PlanningSettingsRepository settings, StepUp stepUp, Clock clock, AuditLog auditLog) {
        this.settings = settings;
        this.stepUp = stepUp;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the settings.
     *
     * <p>Built in M4.
     *
     * @param actor the Admin
     * @param command the new values
     * @param expected the settings' version the Admin saw (BR-76)
     * @throws com.dispatchflow.shared.RuleViolation from M6, with Access's {@code SessionRevoked} or
     *     {@code StepUpRequired}, in that order, before any change (NFR-214)
     * @throws StaleVersion when the settings changed since {@code expected}
     */
    public void change(Actor actor, Command command, Version expected) {
        throw new UnsupportedOperationException("Built in M4");
    }

    /**
     * The settings' new values, each already checked by its own type.
     *
     * <p>Built in M4.
     *
     * @param travelBuckets the travel-time buckets and their factors (FR-321, FR-408)
     * @param buffer the travel-time buffer (FR-227)
     * @param defaultSolveLimit the default daily solve limit, 5 to 60 seconds (FR-19, NFR-06)
     * @param fixedVehicleCost the fixed cost per vehicle used, or off (FR-21)
     * @param departureAllowance the departure allowance (FR-67)
     */
    public record Command(
            TravelBuckets travelBuckets,
            Buffer buffer,
            SolveLimit defaultSolveLimit,
            FixedVehicleCost fixedVehicleCost,
            DepartureAllowance departureAllowance) {

        /** Checks every value is present. */
        public Command {
            Objects.requireNonNull(travelBuckets, "travelBuckets");
            Objects.requireNonNull(buffer, "buffer");
            Objects.requireNonNull(defaultSolveLimit, "defaultSolveLimit");
            Objects.requireNonNull(fixedVehicleCost, "fixedVehicleCost");
            Objects.requireNonNull(departureAllowance, "departureAllowance");
        }
    }
}
