package com.dispatchflow.access.domain;

import java.util.Collection;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The bundled list of the most common passwords that {@link NewPassword} refuses (NFR-95). The
 * adapter that reads the bundled file builds it, because the domain reads no file (DL §3.9).
 *
 * <p>A password is on the list only when it matches an entry exactly. {@link #toString()} shows
 * the count, not the entries.
 */
public final class CommonPasswords {

    private final Set<String> passwords;

    private CommonPasswords(Set<String> passwords) {
        this.passwords = passwords;
    }

    /**
     * Creates the list.
     *
     * @param passwords the entries, in any order; repeats count once
     * @return the list
     */
    public static CommonPasswords of(Collection<String> passwords) {
        return new CommonPasswords(Set.copyOf(passwords));
    }

    /**
     * Whether a password is on the list.
     *
     * @param password the password as typed
     * @return {@code true} when an entry matches it exactly
     */
    public boolean contains(String password) {
        return passwords.contains(password);
    }

    /**
     * How many entries the list holds.
     *
     * @return the count of distinct entries
     */
    public int size() {
        return passwords.size();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof CommonPasswords that && passwords.equals(that.passwords);
    }

    @Override
    public int hashCode() {
        return passwords.hashCode();
    }

    /**
     * A form that names the count only.
     *
     * @return {@code CommonPasswords[<count> entries]}
     */
    @Override
    public String toString() {
        return "CommonPasswords[" + passwords.size() + " entries]";
    }
}
