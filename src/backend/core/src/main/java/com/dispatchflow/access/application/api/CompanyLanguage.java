package com.dispatchflow.access.application.api;

/**
 * The company's default interface language, as other modules see it (FR-125, FR-182, NFR-27,
 * ADR-089): Messaging writes its messages in it, as its {@code MessageLanguage}. Access's domain
 * keeps its own copy, {@code Language}.
 *
 * <p>Built in M2.
 */
public enum CompanyLanguage {
    /** Hebrew, laid out right to left. */
    HEBREW,
    /** English. */
    ENGLISH
}
