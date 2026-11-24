package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.port.out.IssuedToken;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.LinkKind;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * A credential link's email, which a use case's transaction issues and the use case hands to {@code
 * EmailSender.sendLink} only after the commit, so a change that rolls back sends nothing (ADR-129).
 * The token is a secret, never logged (NFR-19, NFR-211), so it prints redacted.
 *
 * <p>To Be Implemented.
 *
 * @param to the address the link goes to: the one stored on the account for a set-password or
 *     recovery link (BR-125), the new one for an email-change link (FR-388)
 * @param kind what the link lets its holder do
 * @param token the link's token
 * @param language the language the email is written in
 */
public record LinkEmail(EmailAddress to, LinkKind kind, IssuedToken token, Language language) implements PersonalData {

    /** Checks every part is present. */
    public LinkEmail {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(language, "language");
    }

    /**
     * A form that hides the address and the token.
     *
     * @return {@code LinkEmail[redacted]}
     */
    @Override
    public String toString() {
        return "LinkEmail[redacted]";
    }
}
