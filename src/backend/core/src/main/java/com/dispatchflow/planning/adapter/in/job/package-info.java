/**
 * Planning's solve workers (ADR-051, ADR-130): one per lane, each running {@code RunSolveJob}, once
 * {@code RecoverSolveJobsAtStartup} has run at startup.
 */
@NullMarked
package com.dispatchflow.planning.adapter.in.job;

import org.jspecify.annotations.NullMarked;
