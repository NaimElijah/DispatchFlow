/**
 * The use cases of Planning's {@code SolveJob} aggregate (DL §4.4, §6.2, §9.2, §12, ADR-051):
 * requesting a re-optimization, the lane worker that runs each job, the queue watchdog, startup
 * recovery, and the progress query.
 *
 * <p>A job's writers race: its worker, the watchdog, startup recovery and the midnight job. The
 * version check refuses the loser, which reloads the job, finds it ended ({@code JobAlreadyEnded})
 * and stops (DL §11).
 */
@NullMarked
package com.dispatchflow.planning.application.service.solvejob;

import org.jspecify.annotations.NullMarked;
