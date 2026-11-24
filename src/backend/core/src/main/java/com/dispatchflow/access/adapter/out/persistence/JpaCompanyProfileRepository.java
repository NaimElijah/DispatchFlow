package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.application.port.out.CompanyProfileRepository;
import com.dispatchflow.access.domain.companyprofile.CompanyProfile;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Keeps the company's one {@link CompanyProfile} in {@code access.company_profile}, under {@link
 * CompanyProfile#KEY}.
 */
@Component
class JpaCompanyProfileRepository implements CompanyProfileRepository {

    private final CompanyProfiles profiles;
    private final VersionedSave versionedSave;

    /**
     * Creates the adapter.
     *
     * @param profiles the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     */
    JpaCompanyProfileRepository(CompanyProfiles profiles, VersionedSave versionedSave) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
    }

    @Override
    public Optional<CompanyProfile> get() {
        return profiles.findById(CompanyProfile.KEY);
    }

    /**
     * {@inheritDoc}
     *
     * <p>It goes through the shared save, which turns a first save that loses the race for the one
     * key into {@code StaleVersion}.
     */
    @Override
    public void add(CompanyProfile profile) {
        versionedSave.save(profile, profiles::save);
    }

    @Override
    public void save(CompanyProfile profile) {
        versionedSave.save(profile, profiles::save);
    }
}
