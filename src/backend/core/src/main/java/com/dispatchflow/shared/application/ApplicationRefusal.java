package com.dispatchflow.shared.application;

/**
 * A refusal the application layer raises rather than the domain, such as a record that doesn't
 * exist or a request key reused with another body: an exception with a stable code, which the web
 * adapter answers with a 4xx, and which Audit's {@code Refusals} records as the refused attempt it
 * is (NFR-23, ADR-150 decision 1). A domain refusal is a {@code RuleViolation} instead.
 *
 * <p>An exception with a code that is no attempt's refusal doesn't implement it: a race, {@code
 * StaleVersion}, is the call wrapper's to record; a substitute a use case answers in a refusal's
 * place, such as {@code SignInRefused}, comes after the refusal's own record; and a defect, such as
 * an optimizer refusing the request the core built, fails loudly as the bug it is. A test lists each
 * of those by name.
 */
public interface ApplicationRefusal {

    /**
     * The refusal's stable code, which the answer and the audit record carry.
     *
     * @return the code, upper snake case
     */
    String code();
}
