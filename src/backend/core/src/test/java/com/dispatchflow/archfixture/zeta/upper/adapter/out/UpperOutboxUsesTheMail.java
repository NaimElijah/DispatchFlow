package com.dispatchflow.archfixture.zeta.upper.adapter.out;

import com.dispatchflow.archfixture.zeta.upper.adapter.out.mail.MailUsesTheStore;

/** Rule 9: a class directly in adapter.out counts as an adapter of its own, so it may not use another. */
public record UpperOutboxUsesTheMail(MailUsesTheStore mail) {}
