package com.dispatchflow.access.application.api;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.application.NotFound;
import java.util.List;

/**
 * Access's answer on whether accounts may drive (DL §2.2): Fleet asks before it registers,
 * rosters or assigns a driver, and Planning before it hands a route over or publishes (FR-06,
 * FR-54, FR-302, BR-20).
 *
 * <p>To Be Implemented.
 */
public interface Accounts {

    /**
     * One account's standing now.
     *
     * <p>To Be Implemented.
     *
     * @param account the account
     * @return its standing
     * @throws NotFound when no account has the ID
     */
    AccountStanding standingOf(AccountId account);

    /**
     * Several accounts' standings now, in one read.
     *
     * <p>To Be Implemented.
     *
     * @param accounts the accounts, each once
     * @return one standing per account, in the order asked
     * @throws NotFound when any of the IDs names no account
     */
    List<AccountStanding> standingsOf(List<AccountId> accounts);
}
