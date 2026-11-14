package com.dispatchflow.access.domain;

import java.util.Objects;

/**
 * One row of the permission matrix under FR-03 (decided 2026-10-11, Q-12, ADR-068), with each
 * role's cell. A tick is {@link Grant#FULL}, a cell that starts "Only" is {@link Grant#LIMITED}, and
 * an empty cell is {@link Grant#NONE}. {@code CapabilityMatrixTest} reads the matrix from
 * {@code requirements.md} and fails when a row has no constant, or a cell disagrees (ADR-104).
 *
 * <p>Each constant's Javadoc quotes its row.
 */
public enum Capability {

    /**
     * "Manage the company's details and its users: rename it, register users, change their roles."
     */
    MANAGE_COMPANY_AND_USERS(Grant.FULL, Grant.NONE, Grant.NONE),

    /**
     * "Read the company's details and its users." A Driver: "Only the company's details and
     * themselves."
     */
    READ_COMPANY_AND_USERS(Grant.FULL, Grant.FULL, Grant.LIMITED),

    /** "Manage depots, vehicles, drivers and shifts; customers and orders." */
    MANAGE_FLEET_CUSTOMERS_AND_ORDERS(Grant.FULL, Grant.FULL, Grant.NONE),

    /**
     * "Plan, solve, edit, publish, cancel a published plan and re-optimize; live views and reports."
     */
    PLAN_AND_RUN_THE_DAY(Grant.FULL, Grant.FULL, Grant.NONE),

    /**
     * "Work their own route: read it, report stops and proofs, report positions." A Driver's tick
     * cites BR-02, which limits it to the routes assigned to the account.
     */
    WORK_OWN_ROUTE(Grant.NONE, Grant.NONE, Grant.FULL),

    /** "Record or correct a stop's outcome on a driver's behalf" (FR-73, FR-333). */
    RECORD_OUTCOME_FOR_DRIVER(Grant.FULL, Grant.FULL, Grant.NONE),

    /** "Confirm or refuse a held backdated report" (BR-132). */
    DECIDE_HELD_REPORT(Grant.FULL, Grant.FULL, Grant.NONE),

    /** "Deactivate or reactivate a user" (FR-53, FR-297). */
    DEACTIVATE_OR_REACTIVATE_USER(Grant.FULL, Grant.NONE, Grant.NONE),

    /** "Send a new user a set-password link" (FR-294). */
    SEND_SET_PASSWORD_LINK(Grant.FULL, Grant.NONE, Grant.NONE),

    /** "Change a user's email" (FR-387). A Dispatcher and a Driver: "Only their own." */
    CHANGE_USER_EMAIL(Grant.FULL, Grant.LIMITED, Grant.LIMITED),

    /** "Retire a driver, which removes only the account's Driver role" (FR-56, FR-307). */
    RETIRE_DRIVER(Grant.FULL, Grant.FULL, Grant.NONE),

    /** "Start and finish their own route, report a problem, confirm a route change" (FR-68, FR-95, FR-97). */
    START_AND_FINISH_OWN_ROUTE(Grant.NONE, Grant.NONE, Grant.FULL),

    /** "View a stop's proof of delivery" (FR-105). A Driver: "Only on their own route." */
    VIEW_PROOF_OF_DELIVERY(Grant.FULL, Grant.FULL, Grant.LIMITED),

    /** "Read recipients' ratings and comments" (FR-360, FR-362). */
    READ_RATINGS(Grant.FULL, Grant.FULL, Grant.NONE),

    /** "See which phones opted out of messages, and record an opt-out" (FR-380). */
    MANAGE_OPT_OUTS(Grant.FULL, Grant.FULL, Grant.NONE),

    /**
     * "Change their own password and language; see and end their own sessions" (FR-123, FR-124,
     * FR-127).
     */
    MANAGE_OWN_ACCOUNT(Grant.FULL, Grant.FULL, Grant.FULL),

    /** "Read the company's settings" (FR-125). A Driver: "Only its name and dispatch phone." */
    READ_COMPANY_SETTINGS(Grant.FULL, Grant.FULL, Grant.LIMITED),

    /**
     * "Change the company's settings, require a second factor, view audit records, end another
     * user's sessions, turn messages to recipients and the rating question on or off" (FR-125,
     * FR-126, FR-128, FR-187, FR-350, FR-375).
     */
    CHANGE_COMPANY_SETTINGS(Grant.FULL, Grant.NONE, Grant.NONE);

    private final Grant admin;
    private final Grant dispatcher;
    private final Grant driver;

    Capability(Grant admin, Grant dispatcher, Grant driver) {
        this.admin = admin;
        this.dispatcher = dispatcher;
        this.driver = driver;
    }

    /**
     * Whether the matrix gives this capability to Admins only: the Admin cell is not empty and the
     * other two are. These are the operations that need the password given in the last 5 minutes
     * (NFR-214; ADR-104).
     *
     * @return {@code true} for an Admin-only row
     */
    public boolean isAdminOnly() {
        return admin != Grant.NONE && dispatcher == Grant.NONE && driver == Grant.NONE;
    }

    /**
     * The cell of one role in this row.
     *
     * @param role the role
     * @return how far that role alone may use this capability
     */
    public Grant grantTo(Role role) {
        return switch (Objects.requireNonNull(role, "role")) {
            case ADMIN -> admin;
            case DISPATCHER -> dispatcher;
            case DRIVER -> driver;
        };
    }
}
