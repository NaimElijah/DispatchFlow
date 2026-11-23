package com.dispatchflow.access.domain.session;

import com.dispatchflow.access.domain.TokenHash;
import java.time.Instant;
import java.util.List;

/**
 * What a test sees of one {@link RefreshToken}, read through its getters: a value that compares by
 * every part, which the entity doesn't.
 *
 * @param id the token's ID
 * @param tokenHash its hash
 * @param issuedAt when it was issued
 * @param state where it stands
 */
public record TokenDescription(RefreshTokenId id, TokenHash tokenHash, Instant issuedAt, TokenState state) {

    /**
     * Describes a token.
     *
     * @param token the token
     * @return what its getters give
     */
    public static TokenDescription of(RefreshToken token) {
        return new TokenDescription(token.id(), token.tokenHash(), token.issuedAt(), token.state());
    }

    /**
     * Describes every token of a session's chain.
     *
     * @param session the session
     * @return each token's description, in the order the tokens were issued
     */
    public static List<TokenDescription> everyTokenOf(Session session) {
        return session.tokens().stream().map(TokenDescription::of).toList();
    }
}
