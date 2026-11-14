/**
 * The shared kernel: the typed IDs every context names, the nine values that mean the same
 * everywhere, and the building blocks every module's domain is made of (DL §2.3 rule 5, §3).
 *
 * <p>It is pure: it imports only the JDK, JSpecify and libphonenumber, and the ArchUnit purity
 * rules check it with every module's domain (DL §3.9). A change here needs the agreement of every
 * module's owner (DL §2.3 rule 5).
 */
@NullMarked
package com.dispatchflow.shared;

import org.jspecify.annotations.NullMarked;
