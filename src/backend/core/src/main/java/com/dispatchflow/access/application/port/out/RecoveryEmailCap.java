package com.dispatchflow.access.application.port.out;

import java.time.LocalDate;

/**
 * NFR-215's daily cap of 100 recovery emails, counted on the send path: only a recovery link that
 * is about to be sent takes from it, so a request for an address no account holds, or a refused
 * one, uses none of it. Other emails never take from it, so they are never held back.
 *
 * <p>The adapter counts the recovery links issued in the Israel calendar day (ADR-088), between
 * bounds computed in {@code Asia/Jerusalem}, because the database session runs in UTC. It counts
 * under a transaction-scoped lock keyed by the day, so two takes at 99 can't both pass, and a
 * restart keeps the count (ADR-131). It alerts the team once a day, the first time the cap
 * refuses a take, and keeps that mark where a restart can't lose it (NFR-215).
 *
 * <p>Built in M6.
 */
public interface RecoveryEmailCap {

    /**
     * Takes one recovery email from a day's cap, when any is left.
     *
     * <p>Built in M6.
     *
     * @param day the Israel calendar day the email is sent on (ADR-088)
     * @return {@code true} while the day is under the cap, and the email may be sent; {@code false}
     *     once it is reached, and nothing is taken
     */
    boolean take(LocalDate day);
}
