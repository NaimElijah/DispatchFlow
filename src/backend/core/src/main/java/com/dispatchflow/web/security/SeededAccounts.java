package com.dispatchflow.web.security;

import com.dispatchflow.StandInProfiles;
import com.dispatchflow.access.application.api.ActorRole;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * The shared list of seeded accounts, as the stand-in session reads it (ADR-133, ADR-147): {@code
 * seeded-accounts.properties}, which the {@code dev} and {@code test} profiles both import, so
 * neither owns a copy. Each entry is a fixed account ID with its roles, under {@value #PREFIX}
 * {@code .<entry>}. The entry's address and language are #181's, which stores one account per
 * entry and binds them in Access's own adapter, since no module may use {@code web}; they are left
 * unread here.
 *
 * @param entries the entries, by name, such as {@code dispatcher}
 */
record SeededAccounts(Map<String, Entry> entries) {

    /** The prefix of every entry's properties. */
    static final String PREFIX = StandInProfiles.SEEDED_ACCOUNTS;

    /** Checks the list is present, and copies it. */
    SeededAccounts {
        entries = Map.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    /**
     * Reads the list from the environment.
     *
     * @param environment the environment, with {@code seeded-accounts.properties} imported
     * @return the list, empty when nothing imported it
     * @throws org.springframework.boot.context.properties.bind.BindException when an entry lacks
     *     its ID or roles, or names a role that doesn't exist
     */
    static SeededAccounts from(Environment environment) {
        return new SeededAccounts(Binder.get(environment)
                .bind(PREFIX, Bindable.mapOf(String.class, Entry.class))
                .orElse(Map.of()));
    }

    /**
     * One seeded account.
     *
     * @param id the account's fixed ID, under which #181 stores it
     * @param roles the account's roles, at least one
     */
    record Entry(UUID id, Set<ActorRole> roles) {

        /** Checks both are present, and that the account has a role. */
        Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(roles, "roles");
            if (roles.isEmpty()) {
                throw new IllegalArgumentException("A seeded account holds at least one role");
            }
            roles = Set.copyOf(roles);
        }
    }
}
