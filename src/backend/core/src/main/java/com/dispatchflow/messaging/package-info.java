/**
 * Messaging, one module of the core (DL §2.1). Its root holds only this {@code package-info} and
 * {@code MessagingConfiguration}, which makes the module's built use cases beans; all its code sits
 * in the packages below ({@code ApplicationRules} rule 15, ADR-140 decision 10).
 */
@NullMarked
package com.dispatchflow.messaging;

import org.jspecify.annotations.NullMarked;
