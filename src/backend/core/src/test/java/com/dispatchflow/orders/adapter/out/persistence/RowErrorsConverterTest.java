package com.dispatchflow.orders.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.orders.domain.OrdersError.AmbiguousAddress;
import com.dispatchflow.orders.domain.OrdersError.BlankAddress;
import com.dispatchflow.orders.domain.OrdersError.BlankContactName;
import com.dispatchflow.orders.domain.OrdersError.DateFull;
import com.dispatchflow.orders.domain.OrdersError.DateInPast;
import com.dispatchflow.orders.domain.OrdersError.DuplicateOrderReference;
import com.dispatchflow.orders.domain.OrdersError.NewCustomerNeedsNameAndPhone;
import com.dispatchflow.orders.domain.OrdersError.NoServiceTime;
import com.dispatchflow.orders.domain.OrdersError.NonPositiveServiceTime;
import com.dispatchflow.orders.domain.OrdersError.RowRefused;
import com.dispatchflow.orders.domain.OrdersError.WindowNotOnDate;
import com.dispatchflow.orders.domain.RowErrors;
import com.dispatchflow.orders.domain.RowNumber;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.SharedKernelError.DateOutOfRange;
import com.dispatchflow.shared.SharedKernelError.InvalidPhoneNumber;
import com.dispatchflow.shared.SharedKernelError.InvalidTimeWindow;
import com.dispatchflow.shared.SharedKernelError.Measure;
import com.dispatchflow.shared.SharedKernelError.NonPositiveQuantity;
import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import com.dispatchflow.shared.SharedKernelError.PriorityOutOfRange;
import com.dispatchflow.shared.SharedKernelError.QuantityOutOfRange;
import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import com.dispatchflow.shared.TimeWindow;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.example.foreign.ForeignError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The JSON column of an import row's errors (ADR-127 decision 7): every error a preview can keep
 * comes back as it was stored, an error the reader would refuse is never stored, and a stored text
 * that names a type the allow-list refuses, or that isn't the converter's form, is damaged state.
 */
class RowErrorsConverterTest {

    private static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2027, 6, 16));
    private static final TimeWindow WINDOW = TimeWindow.on(DATE, LocalTime.of(10, 0), LocalTime.of(12, 0));
    private static final String TYPE_REFUSED = "a stored error names a type that is not a DomainError record";
    private static final String NOT_WELL_FORMED = "a stored error list is not well formed";

    private final RowErrorsConverter converter = new RowErrorsConverter();

    /**
     * Every error a preview can keep with a row: what {@code ImportBatch.check} finds, and what the
     * row's values refuse when the reader builds them from a file's fields.
     */
    static Stream<Arguments> previewErrors() {
        return Stream.<DomainError>of(
                        new NewCustomerNeedsNameAndPhone(),
                        new AmbiguousAddress(List.of(customer(2), customer(1))),
                        new DateInPast(DATE),
                        new WindowNotOnDate(WINDOW, DATE),
                        new NonexistentLocalTime(LocalDateTime.of(2027, 3, 26, 2, 30)),
                        new NoServiceTime(),
                        new DuplicateOrderReference(DATE),
                        new DateFull(DATE, 200),
                        new TextTooLong(TextKind.ADDRESS, 200),
                        new TextHasForbiddenCharacters(TextKind.NOTE),
                        new BlankAddress(),
                        new BlankContactName(),
                        new InvalidPhoneNumber(),
                        new DateOutOfRange(LocalDate.of(10_000, 1, 1)),
                        new InvalidTimeWindow(WINDOW.end(), WINDOW.start()),
                        new NonPositiveQuantity(Measure.WEIGHT),
                        new QuantityOutOfRange(Measure.VOLUME),
                        new PriorityOutOfRange(9),
                        new NonPositiveServiceTime(0))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("previewErrors")
    void GivenAnErrorAPreviewKeeps_WhenStoredAndReadBack_ThenItIsTheSameError(DomainError error) {
        String stored = converter.convertToDatabaseColumn(new RowErrors(List.of(error)));

        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(new RowErrors(List.of(error)));
    }

    @Test
    void GivenSeveralErrors_WhenStoredAndReadBack_ThenTheyKeepTheirOrder() {
        RowErrors errors = new RowErrors(List.of(new DateInPast(DATE), new NoServiceTime(), new DateFull(DATE, 200)));

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(errors)))
                .isEqualTo(errors);
    }

    @Test
    void GivenNoError_WhenStored_ThenTheColumnIsAnEmptyListThatReadsBackAsNone() {
        assertThat(converter.convertToDatabaseColumn(RowErrors.NONE)).isEqualTo("[]");
        assertThat(converter.convertToEntityAttribute("[]")).isEqualTo(RowErrors.NONE);
    }

    @Test
    void GivenAnError_WhenStored_ThenEachErrorIsItsBinaryClassNameAndItsComponents() {
        assertThat(converter.convertToDatabaseColumn(new RowErrors(List.of(new DateInPast(DATE)))))
                .isEqualTo("[{\"type\":\"com.dispatchflow.orders.domain.OrdersError$DateInPast\","
                        + "\"value\":{\"date\":{\"value\":\"2027-06-16\"}}}]");
    }

    /** A {@link DomainError} that is no record, so the reader could not rebuild it by its components. */
    private static final class NotARecord implements DomainError {}

    static Stream<Arguments> errorsTheReaderRefuses() {
        return Stream.of(
                Arguments.of("a DomainError that is no record", new NotARecord()),
                Arguments.of("a DomainError record outside com.dispatchflow", new ForeignError()));
    }

    /** Storing one is the caller's bug, never a refusal, so nothing is written that can't be read back. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("errorsTheReaderRefuses")
    void GivenAnErrorTheReaderRefuses_WhenStored_ThenAssertionError(String kind, DomainError error) {
        RowErrors errors = new RowErrors(List.of(new NoServiceTime(), error));

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(errors))
                .isInstanceOf(AssertionError.class)
                .hasMessage("an import row's error must be a DomainError record under com.dispatchflow,"
                        + " so it can be read back");
    }

    /**
     * An admitted record whose component is an interface: Jackson writes its fields, but can't tell
     * which record to read back, so the row would never load again (ADR-127 decision 7).
     */
    @Test
    void GivenAnErrorWithAComponentTheReaderCanNotRebuild_WhenStored_ThenAssertionError() {
        RowErrors errors = new RowErrors(List.of(new RowRefused(RowNumber.of(3), new NoServiceTime())));

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(errors))
                .isInstanceOf(AssertionError.class)
                .hasMessage("an import row's errors must read back as they were written: RowRefused")
                .hasCauseInstanceOf(StoredStateInvalid.class);
    }

    /**
     * An admitted record that reads back, but not as itself: a record compares an array component
     * by reference, so the array Jackson rebuilds makes another error.
     */
    private record HoldsAnArray(int[] rows) implements DomainError {}

    @Test
    void GivenAnErrorThatReadsBackAsAnotherOne_WhenStored_ThenAssertionError() {
        RowErrors errors = new RowErrors(List.of(new HoldsAnArray(new int[] {3, 4})));

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(errors))
                .isInstanceOf(AssertionError.class)
                .hasMessage("an import row's errors must read back as they were written: HoldsAnArray")
                .hasNoCause();
    }

    @Test
    void GivenANullAttributeOrColumn_WhenConverted_ThenItStaysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // A class outside com.dispatchflow, which the reader never loads.
                "java.lang.ProcessBuilder",
                "org.example.dispatchflow.Gadget",
                // A name that only starts like the package.
                "com.dispatchflowevil.Gadget",
                // A record under com.dispatchflow that is not an error.
                "com.dispatchflow.shared.DeliveryDate",
                // A class under com.dispatchflow that is no record.
                "com.dispatchflow.shared.PlainText",
                // An error type that is no record: the sealed interface itself.
                "com.dispatchflow.orders.domain.OrdersError",
                // No such class.
                "com.dispatchflow.orders.domain.OrdersError$NoSuchError"
            })
    void GivenAStoredTypeTheAllowListRefuses_WhenReadBack_ThenStoredStateInvalid(String type) {
        String stored = "[{\"type\":\"" + type + "\",\"value\":{}}]";

        assertThatThrownBy(() -> converter.convertToEntityAttribute(stored))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("RowErrors");
                    assertThat(invalid.detail()).isEqualTo(TYPE_REFUSED);
                });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not json",
                "{}",
                "[1]",
                "[{\"type\":\"com.dispatchflow.orders.domain.OrdersError$NoServiceTime\"}]",
                "[{\"value\":{}}]",
                "[{\"type\":7,\"value\":{}}]",
                "[{\"type\":\"com.dispatchflow.orders.domain.OrdersError$DateFull\",\"value\":{\"limit\":200}}]",
                "[{\"type\":\"com.dispatchflow.orders.domain.OrdersError$DateFull\","
                        + "\"value\":{\"date\":{\"value\":\"2027-06-16\"},\"limit\":200,\"extra\":1}}]",
                "[{\"type\":\"com.dispatchflow.orders.domain.OrdersError$DateFull\","
                        + "\"value\":{\"date\":{\"value\":\"June\"},\"limit\":200}}]",
                "[] []"
            })
    void GivenAStoredTextNotInTheConvertersForm_WhenReadBack_ThenStoredStateInvalid(String stored) {
        assertThatThrownBy(() -> converter.convertToEntityAttribute(stored))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("RowErrors");
                    assertThat(invalid.detail()).isEqualTo(NOT_WELL_FORMED);
                    assertThat(invalid).hasNoCause();
                });
    }

    @Test
    void GivenAStoredErrorItsOwnTypeNowRefuses_WhenReadBack_ThenStoredStateInvalidNamesTheRule() {
        String stored = "[{\"type\":\"com.dispatchflow.orders.domain.OrdersError$DateInPast\","
                + "\"value\":{\"date\":{\"value\":\"+10000-01-01\"}}}]";

        assertThatThrownBy(() -> converter.convertToEntityAttribute(stored))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("RowErrors");
                    assertThat(invalid.detail()).isEqualTo("a stored value is refused with DATE_OUT_OF_RANGE");
                });
    }

    @Test
    void GivenTheConverter_WhenItsDeclarationIsRead_ThenItIsAutoApplied() {
        Converter declared = RowErrorsConverter.class.getAnnotation(Converter.class);

        assertThat(declared).isNotNull();
        assertThat(declared.autoApply()).isTrue();
    }

    private static CustomerId customer(int n) {
        return CustomerId.of(new UUID(0x0190a5e2_6f72_6465L, n));
    }
}
