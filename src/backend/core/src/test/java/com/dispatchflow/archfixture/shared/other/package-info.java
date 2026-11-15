/**
 * Rule 1: a kernel package other than the kernel's own and its application package may carry no
 * module annotation, so this named interface is flagged.
 */
@NullMarked
@NamedInterface
package com.dispatchflow.archfixture.shared.other;

import com.dispatchflow.archfixture.modulith.NamedInterface;
import org.jspecify.annotations.NullMarked;
