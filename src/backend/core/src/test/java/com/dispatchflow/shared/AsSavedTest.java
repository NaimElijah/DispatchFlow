package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsSavedTest {

    private static final UUID ID = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abc01");
    private static final Instant NOW = Instant.parse("2027-06-15T06:00:00Z");

    record Ticked(EventId eventId, Instant occurredAt) implements DomainEvent {}

    /** An aggregate whose only command raises one event. */
    static final class Ticker extends AggregateRoot {

        static Ticker start() {
            return new Ticker();
        }

        void tick() {
            raise(Ticked.class, ID, id -> new Ticked(id, NOW));
        }
    }

    @Test
    void GivenANeverSavedAggregate_WhenSaved_ThenItIsAtVersionZeroWithNoPendingEvents() {
        Ticker ticker = Ticker.start();
        ticker.tick();

        AsSaved.asSaved(ticker);

        assertThat(ticker.version()).isEqualTo(Version.of(0));
        assertThat(ticker.pendingEvents()).isEmpty();
    }

    @Test
    void GivenASavedAggregate_WhenChangedAndSavedAgain_ThenItsVersionMovesByOneAndItsEventCarriedThatVersion() {
        Ticker ticker = AsSaved.asSaved(Ticker.start());
        ticker.tick();
        EventId raised = ticker.pendingEvents().getFirst().eventId();

        AsSaved.asSaved(ticker);

        assertThat(ticker.version()).isEqualTo(Version.of(1));
        assertThat(raised).isEqualTo(new EventId("Ticked", ID, Version.of(1), 0));
    }
}
