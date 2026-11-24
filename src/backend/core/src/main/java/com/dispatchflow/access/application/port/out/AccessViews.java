package com.dispatchflow.access.application.port.out;

import com.dispatchflow.shared.AccountId;
import java.util.List;
import java.util.Optional;

/**
 * The read models of Access's screens: the signed-in user, the users list and the company's details
 * (DL §3.6, CQRS-lite). It never returns an aggregate; the query classes check who may read what.
 *
 * <p>To Be Implemented.
 */
public interface AccessViews {

    /**
     * The signed-in user's own view (FR-03, FR-181, FR-299).
     *
     * <p>To Be Implemented.
     *
     * @param account the signed-in account
     * @return the view, or empty when no account has the ID
     */
    Optional<MeView> me(AccountId account);

    /**
     * Every account, anonymized ones included (the permission matrix, "Read the company's details
     * and its users").
     *
     * <p>To Be Implemented.
     *
     * @return the accounts, in the order the screen lists them
     */
    List<AccountView> accounts();

    /**
     * One account.
     *
     * <p>To Be Implemented.
     *
     * @param account the account
     * @return the account, or empty when no account has the ID
     */
    Optional<AccountView> account(AccountId account);

    /**
     * The company's details (FR-125).
     *
     * <p>To Be Implemented.
     *
     * @return the details with the profile's version, which every save after the first sends as
     *     {@code If-Match}; or empty before the profile exists, when the first save sends {@code
     *     If-None-Match: *}
     */
    Optional<CompanyView> company();

    /**
     * How many active accounts hold the Admin role, for FR-299's warning (BR-19). It takes no lock:
     * the warning is advice, not a rule.
     *
     * <p>To Be Implemented.
     *
     * @return the count
     */
    int activeAdminCount();
}
