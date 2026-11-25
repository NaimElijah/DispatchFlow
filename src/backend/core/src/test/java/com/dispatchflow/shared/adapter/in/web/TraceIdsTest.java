package com.dispatchflow.shared.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.audit.domain.TraceId;
import org.junit.jupiter.api.Test;

class TraceIdsTest {

    @Test
    void GivenNothingToRead_WhenAFreshTraceIdIsMade_ThenAuditAcceptsIt() {
        String fresh = TraceIds.fresh();

        assertThat(fresh).matches("[0-9a-f]{32}");
        assertThat(TraceId.of(fresh).value()).isEqualTo(fresh);
    }

    @Test
    void GivenTwoFreshTraceIds_WhenCompared_ThenTheyDiffer() {
        assertThat(TraceIds.fresh()).isNotEqualTo(TraceIds.fresh());
    }
}
