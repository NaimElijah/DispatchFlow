package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.AccountStanding;
import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.shared.AccountId;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access's implementation of {@code Accounts}: whether accounts are active and hold the Driver
 * role, which Fleet and Planning translate into their own standings (DL §2.2, FR-06, FR-302,
 * BR-20).
 *
 * <p>Built in M2.
 */
@Transactional(readOnly = true)
public class AccountsService implements Accounts {

    private final UserAccountRepository accounts;

    /**
     * Creates the service.
     *
     * @param accounts where accounts are kept
     */
    public AccountsService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M2.
     */
    @Override
    public AccountStanding standingOf(AccountId account) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M2.
     */
    @Override
    public List<AccountStanding> standingsOf(List<AccountId> accounts) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
