package com.dispatchflow.access.domain.account;

import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.shared.AccountId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What a test sees of a {@link UserAccount}, read through its getters: a value that compares by
 * every part. Tests compare an account before and after a refused command, or after a load with the
 * account that was added, through it. It leaves the version out, which tests check on its own.
 *
 * @param id the account's ID
 * @param email the address it signs in with, until anonymized
 * @param emailSetByAnother which other account's link set the address, and when
 * @param roles its roles
 * @param passwordHash its password's hash, once set and until anonymized
 * @param state where it stands
 * @param language its interface language
 * @param generation its revocation counter
 * @param termsAcceptedAt when it accepted the terms, if it has
 * @param links its credential links, in the order they were sent
 */
public record AccountDescription(
        AccountId id,
        Optional<EmailAddress> email,
        Optional<EmailSetByAnother> emailSetByAnother,
        RoleSet roles,
        Optional<PasswordHash> passwordHash,
        AccountState state,
        Language language,
        AccountGeneration generation,
        Optional<Instant> termsAcceptedAt,
        List<LinkDescription> links) {

    /**
     * Describes an account.
     *
     * @param account the account
     * @return what its getters give
     */
    public static AccountDescription of(UserAccount account) {
        return new AccountDescription(
                account.id(),
                account.email(),
                account.emailSetByAnother(),
                account.roles(),
                account.passwordHash(),
                account.state(),
                account.language(),
                account.generation(),
                account.termsAcceptedAt(),
                LinkDescription.everyLinkOf(account));
    }
}
