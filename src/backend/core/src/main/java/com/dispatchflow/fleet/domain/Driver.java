package com.dispatchflow.fleet.domain;

import com.dispatchflow.fleet.domain.FleetError.DriverInUse;
import com.dispatchflow.fleet.domain.FleetError.NotAnActiveDriver;
import com.dispatchflow.fleet.domain.FleetError.RetentionNotOver;
import com.dispatchflow.fleet.events.DriverRetired;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A person who drives routes (DL §4.2, §17), keyed by their account's ID. Fleet never models the
 * account or its roles: Access owns them, and the use case passes Access's answer in as a {@link
 * Standing}.
 *
 * <p>A driver holds their name and phone, their default shift and vehicle (FR-06, FR-189), and a
 * weekly shift template (FR-79). They end retired, then anonymized a year later (BR-98, NFR-184,
 * ADR-075), or anonymized with their account (NFR-258, ADR-103); the record stays. Every method
 * checks all its rules before it changes anything.
 */
public final class Driver extends AggregateRoot {

    /** How long a retired driver's name and phone are kept (NFR-184, ADR-075). */
    static final Period RETENTION = Period.ofYears(1);

    private final AccountId id;
    private DriverStatus status;
    private @Nullable DriverContact contact;
    private ShiftHours defaultShift;
    private VehicleId defaultVehicle;
    private WeeklyShifts weeklyShifts;

    private Driver(DriverSnapshot stored) {
        super(stored.version());
        this.id = stored.id();
        this.status = stored.status();
        this.contact = stored.contact().orElse(null);
        this.defaultShift = stored.defaultShift();
        this.defaultVehicle = stored.defaultVehicle();
        this.weeklyShifts = stored.weeklyShifts();
    }

    /**
     * Registers a driver for an account (FR-06), with no weekly shifts yet.
     *
     * <p>Whether the default vehicle is in the fleet register is not checked here: the register is
     * another aggregate, and a fill skips a driver whose default vehicle is not active.
     *
     * @param id the account's ID, which keys the driver
     * @param contact their name and phone
     * @param defaultShift the shift hours a date gets by default
     * @param defaultVehicle the vehicle a date gets by default
     * @param account Access's answer: {@link Standing#ACTIVE} when the account is active and holds
     *     the Driver role
     * @return the new driver, active, at {@link Version#NEW}
     * @throws RuleViolation with {@link NotAnActiveDriver} when the account is not an active driver
     */
    public static Driver register(
            AccountId id, DriverContact contact, ShiftHours defaultShift, VehicleId defaultVehicle, Standing account) {
        if (account != Standing.ACTIVE) {
            throw new RuleViolation(new NotAnActiveDriver(id));
        }
        return new Driver(new DriverSnapshot(
                id,
                Version.NEW,
                new DriverStatus.Active(),
                Optional.of(contact),
                defaultShift,
                defaultVehicle,
                WeeklyShifts.none()));
    }

    /**
     * Rebuilds a driver from storage, checking the invariants that hold at rest: the name and phone
     * are present unless the driver is anonymized, and absent once they are; and a driver is not
     * anonymized before being retired. Only persistence adapters call it (DL §3.5).
     *
     * @param stored the stored state
     * @return the driver at the stored version
     * @throws StoredStateInvalid when the stored state breaks one of those invariants
     */
    public static Driver restore(DriverSnapshot stored) {
        boolean anonymized = stored.status() instanceof DriverStatus.Anonymized;
        if (anonymized == stored.contact().isPresent()) {
            throw new StoredStateInvalid(
                    Driver.class, "a driver has a name and phone exactly until they are anonymized");
        }
        if (stored.status() instanceof DriverStatus.Anonymized(Optional<Instant> retiredAt, Instant anonymizedAt)
                && retiredAt.filter(anonymizedAt::isBefore).isPresent()) {
            throw new StoredStateInvalid(Driver.class, "a driver is anonymized only after being retired");
        }
        return new Driver(stored);
    }

    /**
     * The driver's state, for the repository to store and for Fleet's API to read.
     *
     * @return the stored form
     */
    public DriverSnapshot snapshot() {
        return new DriverSnapshot(
                id, version(), status, Optional.ofNullable(contact), defaultShift, defaultVehicle, weeklyShifts);
    }

    /**
     * The driver's key.
     *
     * @return the ID of the driver's account
     */
    public AccountId id() {
        return id;
    }

    /**
     * Changes an active driver's name, phone and defaults (FR-56, FR-06). A change applies to dates
     * filled later; it never changes a value a roster already holds (FR-79).
     *
     * @param newContact their name and phone
     * @param newDefaultShift the shift hours a date gets by default
     * @param newDefaultVehicle the vehicle a date gets by default
     * @throws RuleViolation with {@link NotAnActiveDriver} when the driver is retired or anonymized
     */
    public void changeDetails(DriverContact newContact, ShiftHours newDefaultShift, VehicleId newDefaultVehicle) {
        Objects.requireNonNull(newContact, "newContact");
        Objects.requireNonNull(newDefaultShift, "newDefaultShift");
        Objects.requireNonNull(newDefaultVehicle, "newDefaultVehicle");
        requireActive();
        this.contact = newContact;
        this.defaultShift = newDefaultShift;
        this.defaultVehicle = newDefaultVehicle;
    }

    /**
     * Replaces an active driver's weekly shift template (FR-79, ADR-103).
     *
     * @param newWeeklyShifts the new template; the empty one leaves every date to the default shift
     * @throws RuleViolation with {@link NotAnActiveDriver} when the driver is retired or anonymized
     */
    public void changeWeeklyShifts(WeeklyShifts newWeeklyShifts) {
        Objects.requireNonNull(newWeeklyShifts, "newWeeklyShifts");
        requireActive();
        this.weeklyShifts = newWeeklyShifts;
    }

    /**
     * Retires the driver (BR-43) and raises {@link DriverRetired}, whose listener removes the
     * Driver role from the account (FR-307, ADR-103).
     *
     * @param uses the plans that use the driver, read through {@code RecordUsage}
     * @param today today's date in {@code Asia/Jerusalem}
     * @param now when the driver is retired
     * @throws RuleViolation with {@link NotAnActiveDriver} when the driver is already retired or
     *     anonymized, or {@link DriverInUse} naming the uncancelled plan for today or later that uses
     *     them (FR-305)
     */
    public void retire(PlanUses uses, LocalDate today, Instant now) {
        Objects.requireNonNull(uses, "uses");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(now, "now");
        requireActive();
        uses.blockingOn(today).ifPresent(use -> {
            throw new RuleViolation(new DriverInUse(id, use.plan(), use.date()));
        });
        this.status = new DriverStatus.Retired(now);
        raise(DriverRetired.class, id.value(), eventId -> new DriverRetired(eventId, now, id));
    }

    /**
     * Removes the driver's name and phone at the end of retention, a year after retirement in
     * Israel time (BR-98, NFR-184, ADR-075). The record stays, because plans refer to it. An
     * anonymized driver stays as they are, so the nightly retention job may call this again.
     *
     * @param now when the retention job runs
     * @throws RuleViolation with {@link RetentionNotOver} when the driver is active, or was retired
     *     less than a year ago
     */
    public void anonymize(Instant now) {
        Objects.requireNonNull(now, "now");
        switch (status) {
            case DriverStatus.Active active -> throw new RuleViolation(new RetentionNotOver());
            case DriverStatus.Retired(Instant retiredAt) -> {
                if (now.isBefore(retentionEnd(retiredAt))) {
                    throw new RuleViolation(new RetentionNotOver());
                }
                removePersonalFields(Optional.of(retiredAt), now);
            }
            case DriverStatus.Anonymized anonymized -> {
                // Already done: a repeated nightly run changes nothing.
            }
        }
    }

    /**
     * Removes the driver's name and phone because Access anonymized their account, a year after it
     * was deactivated (NFR-258, ADR-103). Fleet's listener on Access's {@code AccountAnonymized}
     * event calls it, whatever the driver's state: an active driver is anonymized too, and from then
     * on is not active. Access has checked the year, so nothing is checked here. An anonymized driver
     * stays as they are, so a repeated delivery changes nothing.
     *
     * @param now when the account was anonymized
     */
    public void anonymizeWithAccount(Instant now) {
        Objects.requireNonNull(now, "now");
        switch (status) {
            case DriverStatus.Active active -> removePersonalFields(Optional.empty(), now);
            case DriverStatus.Retired(Instant retiredAt) -> removePersonalFields(Optional.of(retiredAt), now);
            case DriverStatus.Anonymized anonymized -> {
                // Already done: the event is delivered at least once.
            }
        }
    }

    /**
     * What this driver's records give a date by default (FR-189, FR-79, ADR-103). With no weekly
     * shifts, every date gets the default shift. With weekly shifts, the date's weekday decides: a
     * day with a shift gets it, and a day without one gets nothing. The vehicle is always the
     * default vehicle, because weekly shifts hold none.
     *
     * @param date the delivery date
     * @param account Access's answer on whether the account is active and holds the Driver role
     *     (ADR-062); the default carries it, so a fill skips a driver whose account is not
     * @return the default, or empty for a day off or a driver record that is retired or anonymized
     */
    public Optional<RosterDefault> rosterDefaultOn(DeliveryDate date, Standing account) {
        Objects.requireNonNull(account, "account");
        if (!(status instanceof DriverStatus.Active)) {
            return Optional.empty();
        }
        Optional<ShiftHours> hours = weeklyShifts.isEmpty()
                ? Optional.of(defaultShift)
                : weeklyShifts.shiftOn(date.value().getDayOfWeek());
        return hours.map(shift -> new RosterDefault(id, shift, defaultVehicle, account));
    }

    private void requireActive() {
        if (!(status instanceof DriverStatus.Active)) {
            throw new RuleViolation(new NotAnActiveDriver(id));
        }
    }

    private void removePersonalFields(Optional<Instant> retiredAt, Instant now) {
        this.status = new DriverStatus.Anonymized(retiredAt, now);
        this.contact = null;
    }

    private static Instant retentionEnd(Instant retiredAt) {
        return retiredAt.atZone(IsraelDateTime.ZONE).plus(RETENTION).toInstant();
    }
}
