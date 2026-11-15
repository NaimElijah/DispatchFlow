package com.dispatchflow.access.domain.account;

import com.dispatchflow.access.domain.AccessError.ActiveAdminExists;
import com.dispatchflow.access.domain.AccessError.TooFewAdmins;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import java.util.Collection;
import java.util.Set;

/**
 * The company's active Admin accounts, read under lock (DL §5): the snapshot the methods that
 * could break BR-19 require, so none of them runs without the lock taken. Only the repository
 * query that takes the lock builds it, which an ArchUnit rule checks (DL §3.9 rule 6).
 *
 * <p>It is a class with a private constructor because a record's canonical constructor would let
 * any caller build one without the lock.
 */
public final class ActiveAdmins {

    /** The fewest active Admins the company keeps (BR-19). */
    public static final int MINIMUM = 2;

    private final Set<AccountId> admins;

    private ActiveAdmins(Set<AccountId> admins) {
        this.admins = admins;
    }

    /**
     * Builds the snapshot from the rows the lock covers.
     *
     * <p>Row locks cover only rows that exist. With no active Admin they cover nothing, so two
     * concurrent first-Admin steps (FR-291) would both read an empty snapshot and both pass {@code
     * checkNoneActive}. The adapter therefore takes a table lock or a transaction-scoped advisory
     * lock, not row locks alone, whenever the read can find no active Admin.
     *
     * @param activeAdmins the active accounts holding the Admin role; repeats count once
     * @return the snapshot
     */
    public static ActiveAdmins readUnderLock(Collection<AccountId> activeAdmins) {
        return new ActiveAdmins(Set.copyOf(activeAdmins));
    }

    /**
     * How many active Admins the company has.
     *
     * @return the count
     */
    public int count() {
        return admins.size();
    }

    /**
     * Checks an account may stop being an active Admin, by losing the role or being deactivated
     * (BR-19, FR-174).
     *
     * @param account the account that would stop, an active Admin
     * @throws RuleViolation with {@link TooFewAdmins} when the company has two or fewer active
     *     Admins
     * @throws AssertionError when the snapshot lacks the account: the caller read the wrong rows, a
     *     bug rather than a refusal
     */
    void checkCanLose(AccountId account) {
        if (!admins.contains(account)) {
            throw new AssertionError("the active Admins read under lock must include the active Admin that changes");
        }
        if (admins.size() <= MINIMUM) {
            throw new RuleViolation(new TooFewAdmins(admins.size()));
        }
    }

    /**
     * Checks no active Admin exists, before the server step creates the first one (FR-291). It is
     * only as safe as the lock {@link #readUnderLock} names for an empty read.
     *
     * @throws RuleViolation with {@link ActiveAdminExists} when one does
     */
    void checkNoneActive() {
        if (!admins.isEmpty()) {
            throw new RuleViolation(new ActiveAdminExists());
        }
    }
}
