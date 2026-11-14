package com.dispatchflow.messaging.domain;

/**
 * The language messages are written in: the company's default interface language, Hebrew or
 * English (FR-349, ADR-089). Access owns that setting; the application layer translates Access's
 * answer into this value (DL §2.3, rule 1).
 */
public enum MessageLanguage {
    /** Hebrew, with ADR-031's wording. */
    HEBREW,
    /** English, with ADR-108's draft wording. */
    ENGLISH
}
