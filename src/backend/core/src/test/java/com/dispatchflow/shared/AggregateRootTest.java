package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.SharedKernelError.PriorityOutOfRange;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregateRootTest {

    private static final UUID ID = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final Instant NOW = Instant.parse("2027-06-15T06:00:00Z");

    record Opened(EventId eventId, Instant occurredAt) implements DomainEvent {}

    sealed interface CounterEvent extends DomainEvent permits Renamed {}

    record Renamed(EventId eventId, Instant occurredAt) implements CounterEvent {}

    /** The smallest aggregate: methods that raise events, refuse, or raise a mislabelled event. */
    static final class Counter extends AggregateRoot {

        private Counter() {}

        private Counter(Version version) {
            super(version);
        }

        static Counter open(Instant now) {
            Counter counter = new Counter();
            counter.raise(Opened.class, ID, id -> new Opened(id, now));
            return counter;
        }

        static Counter loadedAt(Version version) {
            return new Counter(version);
        }

        void renameTwice(Instant now) {
            raise(Renamed.class, ID, id -> new Renamed(id, now));
            raise(Renamed.class, ID, id -> new Renamed(id, now));
        }

        /** Refuses while building its event, as a method that checks a value too late would. */
        void renameRefused(Instant now) {
            raise(Renamed.class, ID, id -> {
                throw new RuleViolation(new PriorityOutOfRange(0));
            });
        }

        void renameUnderItsInterface(Instant now) {
            raise(CounterEvent.class, ID, id -> new Renamed(id, now));
        }

        void renameWithAForeignId(Instant now) {
            raise(Renamed.class, ID, id -> new Renamed(new EventId("Renamed", ID, Version.of(99), 0), now));
        }
    }

    @Test
    void GivenANewAggregate_WhenItsEventIsRead_ThenItCarriesVersionZeroWhichItsFirstSaveStores() {
        Counter counter = Counter.open(NOW);

        assertThat(counter.pendingEvents())
                .containsExactly(new Opened(new EventId("Opened", ID, Version.of(0), 0), NOW));
    }

    @Test
    void GivenANeverSavedAggregate_WhenItsVersionIsRead_ThenItFailsAsAProgrammingError() {
        Counter counter = Counter.open(NOW);

        assertThatThrownBy(counter::version)
                .isInstanceOf(AssertionError.class)
                .hasMessage("an aggregate has no version before its first save");
    }

    @Test
    void GivenANeverSavedAggregate_WhenItsSavedVersionIsRead_ThenThereIsNone() {
        assertThat(Counter.open(NOW).savedVersion()).isEmpty();
    }

    @Test
    void GivenALoadedAggregate_WhenItsSavedVersionIsRead_ThenItIsTheLoadedOne() {
        assertThat(Counter.loadedAt(Version.of(4)).savedVersion()).contains(Version.of(4));
    }

    @Test
    void GivenAnAggregateSavedOnce_WhenItsVersionIsRead_ThenItIsZeroAndItsNextChangeRaisesAtOne() {
        Counter counter = Counter.loadedAt(Version.of(0));

        counter.renameTwice(NOW);

        assertThat(counter.version()).isEqualTo(Version.of(0));
        assertThat(counter.pendingEvents())
                .extracting(DomainEvent::eventId)
                .containsExactly(
                        new EventId("Renamed", ID, Version.of(1), 0), new EventId("Renamed", ID, Version.of(1), 1));
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
    void GivenARefusedCall_WhenAnotherCallRaisesAnEvent_ThenNothingWasLeftBehindAndItsOrdinalIsZero() {
        Counter counter = Counter.loadedAt(Version.of(4));

        assertThatExceptionOfType(RuleViolation.class).isThrownBy(() -> counter.renameRefused(NOW));
        assertThat(counter.pendingEvents()).isEmpty();

        counter.renameTwice(NOW);

        assertThat(counter.pendingEvents())
                .extracting(event -> event.eventId().ordinal())
                .containsExactly(0, 1);
    }

    @Test
    void GivenAnEventBuiltWithAnotherId_WhenRaised_ThenItFailsAsAProgrammingErrorAndIsNotRecorded() {
        Counter counter = Counter.loadedAt(Version.of(4));

        assertThatThrownBy(() -> counter.renameWithAForeignId(NOW)).isInstanceOf(AssertionError.class);
        assertThat(counter.pendingEvents()).isEmpty();
    }

    @Test
    void GivenTheEventsSupertype_WhenRaised_ThenItFailsAsAProgrammingErrorAndIsNotRecorded() {
        Counter counter = Counter.loadedAt(Version.of(4));

        assertThatThrownBy(() -> counter.renameUnderItsInterface(NOW)).isInstanceOf(AssertionError.class);
        assertThat(counter.pendingEvents()).isEmpty();
    }

    @Test
    void GivenRecordedEvents_WhenAFakesSavePublishesThem_ThenTheyAreHandedOverOnceAndCleared() {
        Counter counter = Counter.open(NOW);

        assertThat(SaveEvents.publish(counter)).hasSize(1);
        assertThat(counter.pendingEvents()).isEmpty();
        assertThat(SaveEvents.publish(counter)).isEmpty();
    }

    @Test
    void GivenRecordedEvents_WhenReadWithoutBeingPublished_ThenTheyStay() {
        Counter counter = Counter.open(NOW);

        assertThat(counter.pendingEvents()).hasSize(1);
        assertThat(counter.pendingEvents()).hasSize(1);
    }

    @Test
    void GivenTheListOfPendingEvents_WhenTheCallerTriesToChangeIt_ThenTheAggregateIsUnchanged() {
        Counter counter = Counter.open(NOW);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> counter.pendingEvents().clear());
        assertThat(counter.pendingEvents()).hasSize(1);
    }
}
