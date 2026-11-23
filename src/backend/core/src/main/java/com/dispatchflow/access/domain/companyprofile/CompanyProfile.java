package com.dispatchflow.access.domain.companyprofile;

import com.dispatchflow.access.domain.Language;
import com.dispatchflow.shared.AggregateRoot;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The company's settings that Access owns, kept in one place for the Admins (FR-125, DL §4.1): its
 * name, its default interface language, and the time window and service duration a new customer
 * starts with (FR-184; ADR-104). The other settings of FR-183 belong to the contexts whose rules
 * use them (DL §16). There is one, with no ID, created when the system is deployed.
 *
 * <p>No change raises an event: Customers & Orders reads the defaults through Access's API when it
 * creates a customer, and each change is audited by its use case (FR-186).
 *
 * <p>It is mapped for JPA (ADR-126). Having no ID, it is stored under the constant {@link #KEY}
 * (ADR-127 decision 5).
 */
@Entity
@Table(schema = "access", name = "company_profile")
public class CompanyProfile extends AggregateRoot {

    /**
     * The one stored row's key. The profile has no ID, but every root keys on a UUID (ADR-126
     * decision 10), so it keys on this constant, which the table allows as its only key.
     */
    public static final UUID KEY = UUID.fromString("9ef759c1-f6c0-401c-b963-140ab41b4be2");

    @Id
    private UUID id;

    private CompanyName name;

    @Enumerated(EnumType.STRING)
    private Language language;

    @Embedded
    @AttributeOverride(name = "opens", column = @Column(name = "default_window_opens"))
    @AttributeOverride(name = "closes", column = @Column(name = "default_window_closes"))
    private DefaultWindow defaultWindow;

    private @Nullable DefaultServiceTime defaultServiceTime;

    /** For Hibernate, which sets the fields from the stored row and then runs {@link #checkStored}. */
    protected CompanyProfile() {}

    private CompanyProfile(CompanyName name, Language language, DefaultWindow defaultWindow) {
        this.id = KEY;
        this.name = Objects.requireNonNull(name, "name");
        this.language = Objects.requireNonNull(language, "language");
        this.defaultWindow = Objects.requireNonNull(defaultWindow, "defaultWindow");
    }

    /** Builds the profile {@link #restore} returns; it goes with {@code restore} (ADR-126). */
    private CompanyProfile(CompanyProfileSnapshot stored) {
        super(stored.version());
        this.id = KEY;
        this.name = stored.name();
        this.language = stored.language();
        this.defaultWindow = stored.defaultWindow();
        this.defaultServiceTime = stored.defaultServiceTime().orElse(null);
    }

    /**
     * Creates the profile when the system is deployed. A default service duration is set later,
     * where FR-89 is built (FR-184).
     *
     * @param name the company's name
     * @param language the default interface language
     * @param defaultWindow the time window a new customer starts with
     * @return the new profile, raising no event, never saved
     */
    public static CompanyProfile create(CompanyName name, Language language, DefaultWindow defaultWindow) {
        return new CompanyProfile(name, language, defaultWindow);
    }

    /**
     * Rebuilds the stored profile. Only the persistence adapter calls it (DL §3.5). Its values
     * checked themselves when the adapter built them, and no rule ties them together, so there is
     * nothing more to check.
     *
     * @param stored the stored profile
     * @return the profile at its stored version
     */
    public static CompanyProfile restore(CompanyProfileSnapshot stored) {
        return new CompanyProfile(stored);
    }

    /**
     * The load check every root has (ADR-126 decision 8). Like {@link #restore}, it checks nothing:
     * each value checked itself when its converter or instantiator rebuilt it, and no rule ties them
     * together.
     */
    @PostLoad
    void checkStored() {
        // Deliberately empty: see the Javadoc.
    }

    /**
     * An Admin renames the company (FR-125).
     *
     * @param newName the new name, checked by {@link CompanyName} (FR-382)
     */
    public void rename(CompanyName newName) {
        name = Objects.requireNonNull(newName, "newName");
    }

    /**
     * An Admin changes the language a new user starts with (FR-125, FR-182). Existing users keep
     * their own.
     *
     * @param newLanguage the new default
     */
    public void changeLanguage(Language newLanguage) {
        language = Objects.requireNonNull(newLanguage, "newLanguage");
    }

    /**
     * An Admin changes the time window a new customer starts with (FR-125). Existing customers keep
     * their own.
     *
     * @param newWindow the new default
     */
    public void changeDefaultWindow(DefaultWindow newWindow) {
        defaultWindow = Objects.requireNonNull(newWindow, "newWindow");
    }

    /**
     * An Admin sets the service duration a new customer starts with (FR-184). Existing customers
     * keep their own.
     *
     * @param newTime the new default, checked positive by {@link DefaultServiceTime}
     */
    public void changeDefaultServiceTime(DefaultServiceTime newTime) {
        defaultServiceTime = Objects.requireNonNull(newTime, "newTime");
    }

    /**
     * The company's name, which starts every message to a recipient (FR-382).
     *
     * @return the name
     */
    public CompanyName name() {
        return name;
    }

    /**
     * The language a new user starts with (FR-182).
     *
     * @return the default language
     */
    public Language language() {
        return language;
    }

    /**
     * The time window a new customer starts with (FR-125).
     *
     * @return the default window
     */
    public DefaultWindow defaultWindow() {
        return defaultWindow;
    }

    /**
     * The service duration a new customer starts with (FR-184).
     *
     * @return the default; empty until an Admin sets one
     */
    public Optional<DefaultServiceTime> defaultServiceTime() {
        return Optional.ofNullable(defaultServiceTime);
    }

    /**
     * The profile as it would be stored.
     *
     * @return a snapshot of every field
     */
    public CompanyProfileSnapshot snapshot() {
        return new CompanyProfileSnapshot(
                savedVersion().orElse(null), name, language, defaultWindow, Optional.ofNullable(defaultServiceTime));
    }
}
