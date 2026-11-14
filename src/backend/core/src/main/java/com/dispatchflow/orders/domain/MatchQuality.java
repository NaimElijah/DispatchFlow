package com.dispatchflow.orders.domain;

/** How well the geocoder matched an address, as FR-63 distinguishes it. */
public enum MatchQuality {
    /** The address matched at street level or better. */
    EXACT,
    /** The address matched only below street level, such as the town alone (FR-63). */
    PARTIAL,
    /** The geocoder found several candidates and returned one of them (FR-63). */
    AMBIGUOUS
}
