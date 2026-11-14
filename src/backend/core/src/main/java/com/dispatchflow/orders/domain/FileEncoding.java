package com.dispatchflow.orders.domain;

/** How the file reader decoded an import file (FR-61, FR-313, ADR-086). */
public enum FileEncoding {
    /** A UTF-8 byte order mark, or bytes that decode as strict UTF-8. */
    UTF_8,
    /** Anything else, read as strict Windows-1255; confirming it needs the dispatcher's explicit confirmation (FR-313). */
    WINDOWS_1255
}
