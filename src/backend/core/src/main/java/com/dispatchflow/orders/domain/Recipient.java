package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.PhoneNumber;
import java.util.Objects;
import java.util.Optional;

/**
 * An order's own recipient, who may differ from the customer (FR-87, DL §17): a name, a phone, both
 * or neither. The driver sees them beside the customer's contact. Both are personal data, and both
 * values hide themselves when printed.
 *
 * @param name the recipient's name, if given
 * @param phone the recipient's phone, if given
 */
public record Recipient(Optional<ContactName> name, Optional<PhoneNumber> phone) {

    /** An order with no recipient of its own: the customer receives it. */
    public static final Recipient NONE = new Recipient(Optional.empty(), Optional.empty());

    /** Checks both parts are given as present or empty. */
    public Recipient {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(phone, "phone");
    }
}
