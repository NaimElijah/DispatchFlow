/**
 * The ports Planning owns for other modules to implement (DL §2.1, §2.3 rules 2 and 4): {@link
 * com.dispatchflow.planning.application.spi.ExecutionFacts}, which Live Operations implements, so
 * Planning can read what happened on its routes without depending on Live Operations.
 *
 * <p>Its types use only kernel types, JDK types and their own records and enums (DL §2.3 rule 1).
 * A record that holds a position is personal data and prints redacted (NFR-19, ADR-106).
 */
@NullMarked
package com.dispatchflow.planning.application.spi;

import org.jspecify.annotations.NullMarked;
