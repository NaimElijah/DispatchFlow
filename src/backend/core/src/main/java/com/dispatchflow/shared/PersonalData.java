package com.dispatchflow.shared;

/**
 * Marks a value that identifies or describes a person: a name, a phone number, an address, a
 * location, a free-text note, an email address, or anything else NFR-19 keeps out of logs. Every
 * module makes each of its personal values implement it (ADR-106).
 *
 * <p>The shape rules refuse an error field or an event field whose type is marked, a list of
 * marked values, and an error field whose type holds a marked value at any depth, so personal data
 * never reaches a response's {@code params} or another module's listener (ADR-043, ADR-106). They
 * also require every concrete marked type to declare its own {@code toString()}, which prints it
 * redacted.
 *
 * <p>{@link PlainText} is not marked: whether a piece of text is personal depends on the module
 * value that holds it, and an error refuses it as text anyway.
 */
public interface PersonalData {}
