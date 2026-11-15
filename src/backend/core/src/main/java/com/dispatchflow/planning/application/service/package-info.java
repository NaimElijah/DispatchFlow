/**
 * Planning's application services that several of its aggregates use (TS §2.1, §6.2, DL §6): the
 * {@link com.dispatchflow.planning.application.service.SolveRequestAssembler}, which gathers what the
 * optimizer is asked, and the midnight job {@link
 * com.dispatchflow.planning.application.service.CloseDeliveryDate}, which ends a date's solve jobs and
 * then its plan.
 *
 * <p>The use cases of one aggregate sit in its subpackage: {@code plan}, {@code solvejob}, {@code
 * proposal} and {@code settings} (ADR-118's rule). Each use case is one class: outside calls first,
 * then one transaction that changes one existing aggregate and writes its audit record (TS §6.2, DL
 * §6.1, ADR-063). None carries a Spring annotation yet; M0's first use case decides how the
 * transaction is opened after the outside calls.
 */
@NullMarked
package com.dispatchflow.planning.application.service;

import org.jspecify.annotations.NullMarked;
