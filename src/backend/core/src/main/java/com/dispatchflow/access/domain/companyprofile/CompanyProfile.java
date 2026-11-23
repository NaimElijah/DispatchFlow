package com.dispatchflow.access.domain.companyprofile;

import com.dispatchflow.access.domain.Language;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.Version;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The company's settings that Access owns, kept in one place for the Admins (FR-125, DL §4.1): its
 * name, its default interface language, and the time window and service duration a new customer
 * starts with (FR-184; ADR-104). The other settings of FR-183 belong to the contexts whose rules
 * use them (DL §16). There is one, with no ID, created when the system is deployed.
 *
 * <p>No change raises an event: Customers & Orders reads the defaults through Access's API when it
 * creates a customer, and each change is audited by its use case (FR-186).
 */
public final class CompanyProfile extends AggregateRoot {

    private CompanyName name;
    private Language language;
    private DefaultWindow defaultWindow;
    private @Nullable DefaultServiceTime defaultServiceTime;

    private CompanyProfile(
            @Nullable Version version,
            CompanyName name,
            Language language,
            DefaultWindow defaultWindow,
            @Nullable DefaultServiceTime defaultServiceTime) {
        super(version);
        this.name = Objects.requireNonNull(name, "name");
        this.language = Objects.requireNonNull(language, "language");
        this.defaultWindow = Objects.requireNonNull(defaultWindow, "defaultWindow");
        this.defaultServiceTime = defaultServiceTime;
    }

    /**
     * Creates the profile when the system is deployed. A default service duration is set later,
     * where FR-89 is built (FR-184).
     *
     * @param name the company's name
     * @param language the default interface language
     * @param defaultWindow the time window a new customer starts with
     * @return the new profile, raising no event
     */
    public static CompanyProfile create(CompanyName name, Language language, DefaultWindow defaultWindow) {
        return new CompanyProfile(null, name, language, defaultWindow, null);
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
        return new CompanyProfile(
                stored.version(),
                stored.name(),
                stored.language(),
                stored.defaultWindow(),
                stored.defaultServiceTime().orElse(null));
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
                savedVersion(), name, language, defaultWindow, Optional.ofNullable(defaultServiceTime));
    }
}
