package com.dispatchflow.access.domain.account;

import com.dispatchflow.access.domain.TokenHash;
import java.time.Instant;
import java.util.List;

/**
 * What a test sees of one {@link CredentialLink}, read through its getters: a value that compares
 * by every part, which the entity doesn't.
 *
 * @param id the link's ID
 * @param purpose what it lets its holder do
 * @param tokenHash its token's hash
 * @param issuedAt when it was sent
 * @param expiresAt when it stops working
 * @param status whether it is open, spent or voided
 */
public record LinkDescription(
        CredentialLinkId id,
        LinkPurpose purpose,
        TokenHash tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        LinkStatus status) {

    /**
     * Describes a link.
     *
     * @param link the link
     * @return what its getters give
     */
    public static LinkDescription of(CredentialLink link) {
        return new LinkDescription(
                link.id(), link.purpose(), link.tokenHash(), link.issuedAt(), link.expiresAt(), link.status());
    }

    /**
     * Describes every link of an account.
     *
     * @param account the account
     * @return each link's description, in the order the links were sent
     */
    public static List<LinkDescription> everyLinkOf(UserAccount account) {
        return account.links().stream().map(LinkDescription::of).toList();
    }
}
