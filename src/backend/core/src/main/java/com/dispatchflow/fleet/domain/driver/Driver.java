package com.dispatchflow.fleet.domain.driver;

import com.dispatchflow.fleet.domain.FleetError.DriverInUse;
import com.dispatchflow.fleet.domain.FleetError.NotAnActiveDriver;
import com.dispatchflow.fleet.domain.FleetError.RetentionNotOver;
import com.dispatchflow.fleet.domain.PlanUses;
import com.dispatchflow.fleet.domain.RosterDefault;
import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.Standing;
import com.dispatchflow.fleet.events.DriverRetired;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A person who drives routes (DL §4.2, §17), keyed by their account's ID. Fleet never models the
 * account or its roles: Access owns them, and the use case passes Access's answer in as a {@link
 * Standing}.
 *
 * <p>A driver holds their name and phone, their default shift and vehicle (FR-06, FR-189), and a
 * weekly shift template (FR-79). They end retired, then anonymized a year later (BR-98, NFR-184,
 * ADR-075), or anonymized with their account (NFR-258, ADR-103); the record stays. Every method
 * checks all its rules before it changes anything, and every change ends by checking the invariants
 * at rest.
 *
 * <p>It is mapped for JPA (ADR-126): its key is the account's UUID (ADR-127 decision 5), its {@link
 * DriverStatus} a kind and the two times the states hold, its contact two nullable columns, and its
 * weekly shifts a map keyed by weekday (ADR-127 decision 4). Only Hibernate rebuilds a stored
 * driver, and its load check refuses a damaged one. It holds the driver's name and phone, so {@link
 * #toString()} leaves them out.
 */
@Entity
@Table(schema = "fleet", name = "driver")
public class Driver extends AggregateRoot implements PersonalData {

    /**
     * How long a retired driver's name and phone are kept (NFR-184, ADR-075), counted in Israel's
     * calendar. The persistence adapter reads it to find the drivers due.
     */
    public static final Period RETENTION = Period.ofYears(1);

    /** Which {@link DriverStatus} a driver is in. */
    private enum StatusKind {
        ACTIVE,
        RETIRED,
        ANONYMIZED
    }

    /** The account's UUID. */
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private StatusKind status;

    private @Nullable Instant retiredAt;

    private @Nullable Instant anonymizedAt;

    @Embedded
    private @Nullable DriverContact contact;

    @Embedded
    @AttributeOverride(name = "start", column = @Column(name = "default_shift_starts_at"))
    @AttributeOverride(name = "end", column = @Column(name = "default_shift_ends_at"))
    private ShiftHours defaultShift;

    private VehicleId defaultVehicle;

    @ElementCollection
    @CollectionTable(schema = "fleet", name = "driver_weekly_shift", joinColumns = @JoinColumn(name = "driver_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "day")
    private Map<DayOfWeek, ShiftHours> weeklyShifts = new EnumMap<>(DayOfWeek.class);

    /** For Hibernate, which sets the fields from the stored rows and then runs {@link #checkStored}. */
    protected Driver() {}

    private Driver(AccountId id, DriverContact contact, ShiftHours defaultShift, VehicleId defaultVehicle) {
        this.id = id.value();
        this.status = StatusKind.ACTIVE;
        this.contact = contact;
        this.defaultShift = defaultShift;
        this.defaultVehicle = defaultVehicle;
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
     * @return the new driver, active, never saved
     * @throws RuleViolation with {@link NotAnActiveDriver} when the account is not an active driver
     */
    public static Driver register(
            AccountId id, DriverContact contact, ShiftHours defaultShift, VehicleId defaultVehicle, Standing account) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(contact, "contact");
        Objects.requireNonNull(defaultShift, "defaultShift");
        Objects.requireNonNull(defaultVehicle, "defaultVehicle");
        if (account != Standing.ACTIVE) {
            throw new RuleViolation(new NotAnActiveDriver(id));
        }
        return new Driver(id, contact, defaultShift, defaultVehicle);
    }

    /**
     * Checks the driver as Hibernate loaded them against the invariants that hold at rest (ADR-126
     * decision 8): the name and phone are present unless the driver is anonymized, and absent once
     * they are; and a driver is not anonymized before being retired.
     *
     * @throws StoredStateInvalid when the stored state breaks one of them
     */
    @PostLoad
    void checkStored() {
        brokenRule().ifPresent(broken -> {
            throw new StoredStateInvalid(Driver.class, broken);
        });
    }

    /**
     * The driver's key.
     *
     * @return the ID of the driver's account
     */
    public AccountId id() {
        return AccountId.of(id);
    }

    /**
     * Where the driver is in their lifecycle (DL §9.7).
     *
     * @return active, retired or anonymized, with the times each state holds
     */
    public DriverStatus status() {
        return switch (status) {
            case ACTIVE -> new DriverStatus.Active();
            case RETIRED ->
                new DriverStatus.Retired(Objects.requireNonNull(retiredAt, "a retired driver has a retirement time"));
            case ANONYMIZED ->
                new DriverStatus.Anonymized(
                        Optional.ofNullable(retiredAt),
                        Objects.requireNonNull(anonymizedAt, "an anonymized driver has an anonymization time"));
        };
    }

    /**
     * The driver's name and phone (FR-132).
     *
     * @return them, or empty once the driver is anonymized (BR-98)
     */
    public Optional<DriverContact> contact() {
        return Optional.ofNullable(contact);
    }

    /**
     * The shift hours a date gets by default (FR-06, FR-189).
     *
     * @return the default shift
     */
    public ShiftHours defaultShift() {
        return defaultShift;
    }

    /**
     * The vehicle a date gets by default (FR-06, FR-189).
     *
     * @return the default vehicle
     */
    public VehicleId defaultVehicle() {
        return defaultVehicle;
    }

    /**
     * The driver's weekly shift template (FR-79).
     *
     * @return the template, a copy; the empty one leaves every date to the default shift
     */
    public WeeklyShifts weeklyShifts() {
        return WeeklyShifts.of(weeklyShifts);
    }

    /**
     * A form that names the driver by account and state and leaves their name and phone out.
     *
     * @return the driver without their contact
     */
    @Override
    public String toString() {
        return "Driver[id=" + id() + ", status=" + status() + ", contact="
                + (contact == null ? "none" : "redacted") + ", defaultShift=" + defaultShift
                + ", defaultVehicle=" + defaultVehicle + ", weeklyShifts=" + weeklyShifts() + "]";
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
        requireInvariants();
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
        weeklyShifts.clear();
        weeklyShifts.putAll(newWeeklyShifts.days());
        requireInvariants();
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
        AccountId account = id();
        uses.blockingOn(today).ifPresent(use -> {
            throw new RuleViolation(new DriverInUse(account, use.plan(), use.date()));
        });
        moveTo(new DriverStatus.Retired(now));
        requireInvariants();
        raise(DriverRetired.class, id, eventId -> new DriverRetired(eventId, now, account));
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
        switch (status()) {
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
     * <p>A late, redelivered event can reach a driver a dispatcher retired after the account was
     * anonymized. The driver is then recorded as anonymized at the retirement, never before it, so
     * the saved state keeps the order the load check requires.
     *
     * @param now when the account was anonymized, from the event; for a driver retired later, the
     *     retirement is recorded instead
     */
    public void anonymizeWithAccount(Instant now) {
        Objects.requireNonNull(now, "now");
        switch (status()) {
            case DriverStatus.Active active -> removePersonalFields(Optional.empty(), now);
            case DriverStatus.Retired(Instant retiredAt) ->
                removePersonalFields(Optional.of(retiredAt), now.isBefore(retiredAt) ? retiredAt : now);
            case DriverStatus.Anonymized anonymized -> {
                // Already done: the event is delivered at least once.
            }
        }
    }

    /**
     * What this driver's records give a date by default (FR-189, FR-79, ADR-103). With no weekly
     * shifts, every date gets the default shift. With weekly shifts, the date's weekday decides: a
     * day with a shift gets it, and a day without one gets no hours. The vehicle is always the
     * default vehicle, because weekly shifts hold none, so a day off still gives it to an entry a
     * dispatcher made by hand.
     *
     * @param date the delivery date
     * @param account Access's answer on whether the account is active and holds the Driver role
     *     (ADR-062); the default carries it, so a fill skips a driver whose account is not
     * @return the default, or empty for a driver record that is retired or anonymized
     */
    public Optional<RosterDefault> rosterDefaultOn(DeliveryDate date, Standing account) {
        Objects.requireNonNull(account, "account");
        if (status != StatusKind.ACTIVE) {
            return Optional.empty();
        }
        Optional<ShiftHours> hours = weeklyShifts.isEmpty()
                ? Optional.of(defaultShift)
                : Optional.ofNullable(weeklyShifts.get(date.value().getDayOfWeek()));
        return Optional.of(new RosterDefault(id(), hours, defaultVehicle, account));
    }

    /** Sets the status's kind and the times it holds, in place. */
    private void moveTo(DriverStatus next) {
        retiredAt = null;
        anonymizedAt = null;
        status = switch (next) {
            case DriverStatus.Active active -> StatusKind.ACTIVE;
            case DriverStatus.Retired retired -> {
                retiredAt = retired.retiredAt();
                yield StatusKind.RETIRED;
            }
            case DriverStatus.Anonymized anonymized -> {
                retiredAt = anonymized.retiredAt().orElse(null);
                anonymizedAt = anonymized.anonymizedAt();
                yield StatusKind.ANONYMIZED;
            }
        };
    }

    private void requireActive() {
        if (status != StatusKind.ACTIVE) {
            throw new RuleViolation(new NotAnActiveDriver(id()));
        }
    }

    private void removePersonalFields(Optional<Instant> retiredAt, Instant now) {
        moveTo(new DriverStatus.Anonymized(retiredAt, now));
        this.contact = null;
        requireInvariants();
    }

    /**
     * The first invariant at rest the driver breaks, if any: the name and phone are present unless
     * the driver is anonymized, and absent once they are; and a driver is not anonymized before
     * being retired.
     */
    private Optional<String> brokenRule() {
        if ((status == StatusKind.ANONYMIZED) == (contact != null)) {
            return Optional.of("a driver has a name and phone exactly until they are anonymized");
        }
        if (status == StatusKind.ANONYMIZED
                && retiredAt != null
                && anonymizedAt != null
                && anonymizedAt.isBefore(retiredAt)) {
            return Optional.of("a driver is anonymized only after being retired");
        }
        return Optional.empty();
    }

    /**
     * Checks the state a command left against every invariant at rest (ADR-126 decision 11). Every
     * rule a caller can break was checked before the command changed anything, so a broken
     * invariant here is a bug in the command, not a refusal, and the error rolls its transaction
     * back.
     */
    private void requireInvariants() {
        brokenRule().ifPresent(broken -> {
            throw new AssertionError("a driver command left a state that breaks: " + broken);
        });
    }

    private static Instant retentionEnd(Instant retiredAt) {
        return retiredAt.atZone(IsraelDateTime.ZONE).plus(RETENTION).toInstant();
    }
}
