package com.dispatchflow.archfixture.zeta.upper.adapter.out.mail;

import com.dispatchflow.archfixture.zeta.upper.adapter.out.store.UpperTable;

/** Rule 9: one outbound adapter uses another of its module. */
public record MailUsesTheStore(UpperTable table) {}
