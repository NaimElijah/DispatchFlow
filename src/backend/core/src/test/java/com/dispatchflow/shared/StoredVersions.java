package com.dispatchflow.shared;

/** The version a save stores, for the fixtures that save and reload an aggregate in memory. */
public final class StoredVersions {

    private StoredVersions() {}

    /**
     * The version saving an aggregate stores: 0 for one never saved, and its stored version plus 1
     * otherwise (ADR-126).
     *
     * @param aggregate the aggregate being saved
     * @return the version the save stores
     */
    public static Version afterSave(AggregateRoot aggregate) {
        return aggregate.savedVersion().map(Version::next).orElse(Version.of(0));
    }
}
