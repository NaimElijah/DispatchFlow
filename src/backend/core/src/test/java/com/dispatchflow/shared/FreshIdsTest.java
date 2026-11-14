package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FreshIdsTest {

    private static final UUID FIRST = UUID.fromString("0190a5e2-0000-7000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("0190a5e2-0000-7000-8000-000000000002");

    @Test
    void GivenASupply_WhenDrawn_ThenTheIdsComeInTheOrderGiven() {
        FreshIds ids = FreshIds.of(List.of(FIRST, SECOND));

        assertThat(ids.next()).isEqualTo(FIRST);
        assertThat(ids.remaining()).isEqualTo(1);
        assertThat(ids.next()).isEqualTo(SECOND);
        assertThat(ids.remaining()).isZero();
    }

    @Test
    void GivenAUsedUpSupply_WhenDrawnFrom_ThenItFailsAsAProgrammingError() {
        FreshIds ids = FreshIds.of(List.of(FIRST));
        ids.next();

        assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(ids::next);
    }

    @Test
    void GivenTheCallersListChangesAfterward_WhenDrawn_ThenTheSupplyIsUnchanged() {
        List<UUID> given = new ArrayList<>(List.of(FIRST));
        FreshIds ids = FreshIds.of(given);

        given.add(SECOND);

        assertThat(ids.remaining()).isEqualTo(1);
    }
}
