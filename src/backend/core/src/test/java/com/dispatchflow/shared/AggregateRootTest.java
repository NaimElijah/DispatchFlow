package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregateRootTest {

    private static final UUID ID = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final Instant NOW = Instant.parse("2027-06-15T06:00:00Z");

    record Opened(EventId eventId, Instant occurredAt) implements DomainEvent {}

    record Renamed(EventId eventId, Instant occurredAt) implements DomainEvent {}

    /** The smallest aggregate: one method that raises two events in one change. */
    static final class Counter extends AggregateRoot {

        private Counter(Version version) {
            super(version);
        }

        static Counter open(Instant now) {
            Counter counter = new Counter(Version.NEW);
            counter.raise(new Opened(counter.nextEventId(Opened.class, ID), now));
            return counter;
        }

        static Counter loadedAt(Version version) {
            return new Counter(version);
        }

        void renameTwice(Instant now) {
            raise(new Renamed(nextEventId(Renamed.class, ID), now));
            raise(new Renamed(nextEventId(Renamed.class, ID), now));
        }
    }

    @Test
    void GivenANewAggregate_WhenItsEventIsRead_ThenItCarriesTheFirstSavedVersion() {
        Counter counter = Counter.open(NOW);

        assertThat(counter.version()).isEqualTo(Version.NEW);
        assertThat(counter.pendingEvents())
                .containsExactly(new Opened(new EventId("Opened", ID, Version.of(1), 0), NOW));
    }

    @Test
    void GivenALoadedAggregate_WhenOneChangeRaisesTwoEvents_ThenTheirOrdinalsCountUpAndBothCarryTheNextVersion() {
        Counter counter = Counter.loadedAt(Version.of(4));

        counter.renameTwice(NOW);

        assertThat(counter.pendingEvents())
                .containsExactly(
                        new Renamed(new EventId("Renamed", ID, Version.of(5), 0), NOW),
                        new Renamed(new EventId("Renamed", ID, Version.of(5), 1), NOW));
        assertThat(counter.version()).isEqualTo(Version.of(4));
    }

    @Test
    void GivenRecordedEvents_WhenPulled_ThenTheyAreReturnedOnceAndCleared() {
        Counter counter = Counter.open(NOW);

        assertThat(counter.pullEvents()).hasSize(1);
        assertThat(counter.pullEvents()).isEmpty();
        assertThat(counter.pendingEvents()).isEmpty();
    }

    @Test
    void GivenRecordedEvents_WhenReadWithoutPulling_ThenTheyStay() {
        Counter counter = Counter.open(NOW);

        assertThat(counter.pendingEvents()).hasSize(1);
        assertThat(counter.pendingEvents()).hasSize(1);
    }
}
