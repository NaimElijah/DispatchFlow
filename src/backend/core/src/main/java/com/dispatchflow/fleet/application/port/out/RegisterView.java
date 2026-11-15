package com.dispatchflow.fleet.application.port.out;

import com.dispatchflow.fleet.domain.register.DepotHours;
import com.dispatchflow.fleet.domain.register.DepotName;
import com.dispatchflow.fleet.domain.register.VehicleDetails;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The fleet register as its screen shows it: every depot and vehicle, active or retired (FR-56,
 * FR-193). The version is the one every register change sends back (BR-76).
 *
 * <p>Built in M2.
 *
 * @param depots every depot, in the order they were added
 * @param vehicles every vehicle, in the order they were added
 * @param version the version the register was read at, for {@code If-Match} (BR-76); {@code
 *     Version.NEW} before the register is first stored
 */
public record RegisterView(List<DepotRow> depots, List<VehicleRow> vehicles, Version version) {

    /** Checks the version is present, and copies the lists. */
    public RegisterView {
        Objects.requireNonNull(version, "version");
        depots = List.copyOf(depots);
        vehicles = List.copyOf(vehicles);
    }

    /**
     * One depot as the register screen shows it (FR-04, FR-129, FR-197). Its location is a kernel
     * {@code GeoPoint}, so it prints redacted.
     *
     * <p>Built in M2.
     *
     * @param depot the depot
     * @param name its name
     * @param location where it is, or empty until it is placed (FR-130, FR-197)
     * @param hours its opening hours, or empty when it sets none (FR-129)
     * @param active whether it is active; a retired one stays on past plans (FR-193, BR-43)
     */
    public record DepotRow(
            DepotId depot, DepotName name, Optional<GeoPoint> location, Optional<DepotHours> hours, boolean active)
            implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public DepotRow {
            Objects.requireNonNull(depot, "depot");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(hours, "hours");
        }

        /**
         * A form that hides the location.
         *
         * @return {@code RegisterView.DepotRow[redacted]}
         */
        @Override
        public String toString() {
            return "RegisterView.DepotRow[redacted]";
        }
    }

    /**
     * One vehicle as the register screen shows it (FR-05, FR-131, FR-198).
     *
     * <p>Built in M2.
     *
     * @param vehicle the vehicle
     * @param details its name, plate, capacity and depots
     * @param active whether it is active, so new plans may use it (FR-56, FR-193)
     */
    public record VehicleRow(VehicleId vehicle, VehicleDetails details, boolean active) {

        /** Checks every part is present. */
        public VehicleRow {
            Objects.requireNonNull(vehicle, "vehicle");
            Objects.requireNonNull(details, "details");
        }
    }
}
