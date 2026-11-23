/**
 * The shared kernel: the typed IDs every context names, the ten values that mean the same
 * everywhere, {@link Attempt} among them (ADR-111), the one rule for free text ({@link PlainText},
 * ADR-106), and the building blocks every module's domain is made of (DL §2.3 rule 5, §3). The
 * ports and errors every module's application layer shares live in their own package, {@code
 * shared.application}, which no domain type may import (ADR-120).
 *
 * <p>It is pure: it imports only the JDK, JSpecify and libphonenumber, and carries only the
 * persistence annotations ADR-126 allows, and the ArchUnit purity rules check it with every module's
 * domain (DL §3.9). The persistence adapter beside it, {@code shared.adapter.out.persistence}, is no
 * part of it, and it uses none of that adapter's classes. A change here needs the agreement of every
 * module's owner (DL §2.3 rule 5).
 */
@NullMarked
package com.dispatchflow.shared;

import org.jspecify.annotations.NullMarked;
