/**
 * Planning's application services that several of its aggregates use (TS §2.1, §6.2, DL §6): the
 * {@link com.dispatchflow.planning.application.service.SolveRequestAssembler}, which gathers what the
 * optimizer is asked, and the midnight job {@link
 * com.dispatchflow.planning.application.service.CloseDeliveryDate}, which ends a date's solve jobs and
 * then its plan, and the retention step {@link
 * com.dispatchflow.planning.application.service.EraseExpiredLocations}, which ends Planning's copies of
 * coordinates and positions (ADR-121).
 *
 * <p>The use cases of one aggregate sit in its subpackage: {@code plan}, {@code solvejob}, {@code
 * proposal} and {@code settings} (ADR-118's rule). Each use case makes its outside calls first, then
 * one transaction that changes one existing aggregate and writes its audit record (TS §6.2, DL §6.1,
 * ADR-063). Every service carries a class-level {@code @Transactional}, read-only when it changes
 * nothing. A use case with outside calls, or with one transaction per item, is two classes: the use
 * case, with none of its own, and its {@code <UseCase>Change} beside it, which holds the transaction
 * (ADR-129).
 */
@NullMarked
package com.dispatchflow.planning.application.service;

import org.jspecify.annotations.NullMarked;
