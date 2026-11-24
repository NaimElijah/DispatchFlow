/**
 * Customers & Orders' server steps (ADR-130, ADR-131), which a person runs at a prompt or from a
 * runbook: its part of the steps that span every module, the personal-data requests (NFR-49,
 * NFR-185), the company's export and its deletion (NFR-150) and the pilot-day export (FR-289). Each
 * step works on this module's own personal columns, as the data inventory lists them, in one
 * audited transaction of its own, and a rerun completes a run that stopped. One runbook script runs
 * every module's step in order (ADR-131). The package leaves their use cases until the steps are
 * designed (ADR-120 decision 9).
 */
@NullMarked
package com.dispatchflow.orders.adapter.in.cli;

import org.jspecify.annotations.NullMarked;
