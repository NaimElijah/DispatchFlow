package com.dispatchflow.access.domain.account;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The roles an account holds: any combination of Admin, Dispatcher and Driver, or none once its
 * last role is removed (FR-02, FR-293, BR-123). It answers the permission matrix (NFR-85, FR-03):
 * an account may do everything any of its roles allows.
 *
 * @param roles the roles, in declaration order; unmodifiable
 */
public record RoleSet(Set<Role> roles) {

    /**
     * Copies the roles into a set in declaration order.
     *
     * @throws NullPointerException when the set or a role is {@code null}
     */
    public RoleSet {
        EnumSet<Role> copy = EnumSet.noneOf(Role.class);
        copy.addAll(List.copyOf(Objects.requireNonNull(roles, "roles")));
        roles = Collections.unmodifiableSet(copy);
    }

    /**
     * Creates a set of roles.
     *
     * @param roles the roles; repeats count once
     * @return the set
     */
    public static RoleSet of(Role... roles) {
        return new RoleSet(Set.copyOf(List.of(roles)));
    }

    /**
     * Creates a set of roles.
     *
     * @param roles the roles; repeats count once
     * @return the set
     */
    public static RoleSet of(Collection<Role> roles) {
        return new RoleSet(Set.copyOf(roles));
    }

    /**
     * Whether the account may use a capability at all (FR-03, FR-293). A limited grant counts:
     * the use case then checks the limit (ADR-068).
     *
     * @param capability the matrix row
     * @return {@code false} for every capability while the set is empty
     */
    public boolean can(Capability capability) {
        return grant(capability) != Grant.NONE;
    }

    /**
     * How far the account may use a capability: the widest cell among its roles (FR-293).
     *
     * @param capability the matrix row
     * @return {@link Grant#NONE} while the set is empty
     */
    public Grant grant(Capability capability) {
        Objects.requireNonNull(capability, "capability");
        Grant widest = Grant.NONE;
        for (Role role : roles) {
            widest = widest.widest(capability.grantTo(role));
        }
        return widest;
    }

    /**
     * Whether the set holds a role.
     *
     * @param role the role
     * @return {@code true} when held
     */
    public boolean contains(Role role) {
        return roles.contains(role);
    }

    /**
     * Whether the set holds no role, so the account can't sign in (BR-123).
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return roles.isEmpty();
    }
}
