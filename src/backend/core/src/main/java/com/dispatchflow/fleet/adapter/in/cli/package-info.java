/**
 * Fleet's server steps (ADR-130, ADR-131), which a person runs at a prompt or from a runbook: its
 * part of the steps that span every module, the personal-data requests (NFR-49, NFR-185), the
 * company's export and its deletion (NFR-150) and the pilot-day export (FR-289). Each step works on
 * this module's own personal columns, as the data inventory lists them, in one audited transaction
 * of its own, and a rerun completes a run that stopped. One runbook script runs every module's step
 * in order (ADR-131). ADR-120 decision 9 leaves their use cases to the milestone that designs them.
 */
@NullMarked
package com.dispatchflow.fleet.adapter.in.cli;

import org.jspecify.annotations.NullMarked;
