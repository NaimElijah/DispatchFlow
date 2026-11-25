/**
 * The web code every module's controllers share (ADR-043, ADR-140): the code generated from {@code
 * client-api}, each module's {@code <Tag>Api} interface in {@code .api} and the models in {@code
 * .model}, generated at every build and never committed (#161), and {@code @RequiresCapability},
 * which the generated interfaces carry (ADR-068, #171). It sits beside the kernel, below {@code
 * web}, so every module's {@code in/web} controller implements its interfaces without depending on
 * {@code web}. Its types stop at those controllers and {@code web}, and never reach a use case.
 */
@NullMarked
package com.dispatchflow.shared.adapter.in.web;

import org.jspecify.annotations.NullMarked;
