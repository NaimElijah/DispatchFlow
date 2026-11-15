package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.ImportBatchBuilder.BATCH;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.HASH;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.KNOWN_ADDRESS;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.KNOWN_CUSTOMER;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.NEW_ADDRESS;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.SHARED_ADDRESS;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.anImportBatch;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.cleanRow;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.context;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.inMinutes;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.row;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.rowWithReference;
import static com.dispatchflow.orders.domain.ImportBatchBuilder.rowWithReferenceOn;
import static com.dispatchflow.orders.domain.OrdersTestData.DEFAULTS;
import static com.dispatchflow.orders.domain.OrdersTestData.NEXT_WEEK;
import static com.dispatchflow.orders.domain.OrdersTestData.NOW;
import static com.dispatchflow.orders.domain.OrdersTestData.REFERENCE;
import static com.dispatchflow.orders.domain.OrdersTestData.TODAY;
import static com.dispatchflow.orders.domain.OrdersTestData.TOMORROW;
import static com.dispatchflow.orders.domain.OrdersTestData.YESTERDAY;
import static com.dispatchflow.orders.domain.OrdersTestData.bareDetails;
import static com.dispatchflow.orders.domain.OrdersTestData.customerId;
import static com.dispatchflow.orders.domain.OrdersTestData.dateWith;
import static com.dispatchflow.orders.domain.OrdersTestData.dateWithReference;
import static com.dispatchflow.orders.domain.OrdersTestData.details;
import static com.dispatchflow.orders.domain.OrdersTestData.emptyDate;
import static com.dispatchflow.shared.DomainAssertions.assertAllRefused;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.dispatchflow.orders.domain.OrdersError.AmbiguousAddress;
import com.dispatchflow.orders.domain.OrdersError.DateFull;
import com.dispatchflow.orders.domain.OrdersError.DateInPast;
import com.dispatchflow.orders.domain.OrdersError.DuplicateOrderReference;
import com.dispatchflow.orders.domain.OrdersError.EncodingNotConfirmed;
import com.dispatchflow.orders.domain.OrdersError.ImportAlreadyConfirmed;
import com.dispatchflow.orders.domain.OrdersError.ImportDiscarded;
import com.dispatchflow.orders.domain.OrdersError.ImportExpired;
import com.dispatchflow.orders.domain.OrdersError.ImportHasErrors;
import com.dispatchflow.orders.domain.OrdersError.ImportNotYetExpired;
import com.dispatchflow.orders.domain.OrdersError.NewCustomerNeedsNameAndPhone;
import com.dispatchflow.orders.domain.OrdersError.NoServiceTime;
import com.dispatchflow.orders.domain.OrdersError.RowRefused;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.FreshIds;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.Measure;
import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import com.dispatchflow.shared.SharedKernelError.QuantityOutOfRange;
import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Weight;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link ImportBatch}'s invariants (DL §4.3, §9.4), ADR-086's rules and ADR-102's point on decimals. */
class ImportBatchTest {

    private static final Instant HALF_AN_HOUR_LATER = inMinutes(30);

    private static FreshIds ids(int count) {
        return FreshIds.of(IntStream.rangeClosed(1, count)
                .mapToObj(n -> new UUID(0x0190a5e200007000L, 0x80000000000f0000L | n))
                .toList());
    }

    private static UUID fresh(int n) {
        return new UUID(0x0190a5e200007000L, 0x80000000000f0000L | n);
    }

    private static void assertRefusedUnchanged(ImportBatch batch, ThrowingCallable call, DomainError expected) {
        ImportBatchSnapshot before = batch.snapshot();
        assertRefused(call, expected);
        assertThat(batch.snapshot()).isEqualTo(before);
        assertThat(batch.pendingEvents()).isEmpty();
    }

    private static void assertAllRefusedUnchanged(
            ImportBatch batch, ThrowingCallable call, DomainError summary, List<? extends DomainError> errors) {
        ImportBatchSnapshot before = batch.snapshot();
        assertAllRefused(call, summary, errors);
        assertThat(batch.snapshot()).isEqualTo(before);
        assertThat(batch.pendingEvents()).isEmpty();
    }

    @Nested
    class Previewing {

        @Test
        void GivenCleanRows_WhenPreviewed_ThenTheBatchIsPreviewedAndNoRowHasAnError() {
            ImportBatch batch =
                    anImportBatch().withRows(cleanRow(1), cleanRow(2)).previewed();

            assertThat(batch.state()).isEqualTo(new ImportBatch.State.Previewed());
            assertThat(batch.rows()).extracting(PreviewRow::errors).containsExactly(List.of(), List.of());
            assertThat(batch.snapshot().version()).isEqualTo(Version.NEW);
            assertThat(batch.pendingEvents()).isEmpty();
        }

        @Test
        void GivenRowsOutOfOrder_WhenPreviewed_ThenTheyAreKeptInRowOrder() {
            ImportBatch batch =
                    anImportBatch().withRows(cleanRow(3), cleanRow(1)).previewed();

            assertThat(batch.rows())
                    .extracting(previewRow -> previewRow.row().number())
                    .containsExactly(RowNumber.of(1), RowNumber.of(3));
        }

        @Test
        void GivenTwoRowsWithOneNumber_WhenPreviewed_ThenItIsRefusedAsAReaderBug() {
            ImportBatchBuilder twice = anImportBatch().withRows(cleanRow(2), cleanRow(2));

            assertThatThrownBy(twice::previewed)
                    .isInstanceOfSatisfying(
                            StoredStateInvalid.class,
                            invalid -> assertThat(invalid.detail()).isEqualTo("two rows share a number"));
        }

        @Test
        void GivenRowsWithErrors_WhenPreviewed_ThenEachRowKeepsItsErrorsAndNothingIsRefused() {
            DomainError threeDecimals = new QuantityOutOfRange(Measure.WEIGHT);
            ImportBatch batch = anImportBatch()
                    .withRows(
                            new ImportRow.Unreadable(RowNumber.of(1), List.of(threeDecimals)),
                            row(2, SHARED_ADDRESS, TOMORROW, bareDetails()))
                    .previewed();

            assertThat(batch.rows())
                    .extracting(PreviewRow::errors)
                    .containsExactly(
                            List.of(threeDecimals),
                            List.of(new AmbiguousAddress(List.of(customerId(2), customerId(3)))));
        }
    }

    @Nested
    class Confirming {

        @Test
        void GivenRowsForAKnownAndANewAddress_WhenConfirmed_ThenTheNewCustomerAndEveryOrderAreCreated() {
            ImportBatch batch = anImportBatch()
                    .withRows(cleanRow(1), rowWithReference(2, REFERENCE))
                    .previewed();

            ImportResult result = batch.confirm(context(), false, ids(3), TODAY, HALF_AN_HOUR_LATER);

            assertThat(result.customers()).hasSize(1);
            Customer created = result.customers().get(0);
            assertThat(created.id()).isEqualTo(CustomerId.of(fresh(1)));
            assertThat(created.flags()).containsExactly(AddressFlag.CREATED_BY_IMPORT);
            assertThat(created.orderDefaults()).isEqualTo(DEFAULTS);
            assertThat(created.snapshot().name()).contains(ImportBatchBuilder.NAME);
            assertThat(created.snapshot().phone()).contains(ImportBatchBuilder.PHONE);
            assertThat(created.snapshot().address()).contains(NEW_ADDRESS);
            assertThat(created.snapshot().createdAt()).isEqualTo(HALF_AN_HOUR_LATER);
            assertThat(result.orders())
                    .extracting(Order::id, Order::customer)
                    .containsExactly(
                            tuple(OrderId.of(fresh(2)), CustomerId.of(fresh(1))),
                            tuple(OrderId.of(fresh(3)), KNOWN_CUSTOMER.id()));
            assertThat(result.orders().get(0).snapshot().serviceTime()).isEqualTo(OrdersTestData.FIVE_MINUTES);
            assertThat(result.orders())
                    .allSatisfy(order -> assertThat(order.lastChangedAt()).isEqualTo(HALF_AN_HOUR_LATER));
            assertThat(batch.state()).isEqualTo(new ImportBatch.State.Confirmed(HALF_AN_HOUR_LATER));
            assertThat(batch.rows()).isEmpty();
        }

        @Test
        void GivenOneReferenceOnTwoDates_WhenConfirmed_ThenBothRowsImport() {
            ImportBatch batch = anImportBatch()
                    .withRows(rowWithReference(1, REFERENCE), rowWithReferenceOn(2, REFERENCE, NEXT_WEEK))
                    .previewedWith(context(emptyDate(TOMORROW), emptyDate(NEXT_WEEK)));

            ImportResult result = batch.confirm(
                    context(emptyDate(TOMORROW), emptyDate(NEXT_WEEK)), false, ids(2), TODAY, HALF_AN_HOUR_LATER);

            assertThat(result.orders()).extracting(Order::date).containsExactly(TOMORROW, NEXT_WEEK);
        }

        @Test
        void GivenAKnownCustomerWithItsOwnDefaultWindow_WhenARowGivesNone_ThenTheOrderTakesThatWindow() {
            DailyWindow early = DailyWindow.of(LocalTime.of(7, 0), LocalTime.of(8, 0));
            CustomerMatch.Candidate earlyBird = new CustomerMatch.Candidate(
                    customerId(7),
                    ImportBatchBuilder.NAME,
                    ImportBatchBuilder.PHONE,
                    new OrderDefaults(early, Optional.of(OrdersTestData.FIVE_MINUTES)));
            ImportContext context = ImportContext.of(
                    List.of(new ImportContext.AddressMatch(
                            NormalizedAddress.of(KNOWN_ADDRESS), new CustomerMatch.OneCustomer(earlyBird))),
                    List.of(emptyDate(TOMORROW)),
                    DEFAULTS);
            ImportBatch batch = anImportBatch()
                    .withRows(row(1, KNOWN_ADDRESS, TOMORROW, bareDetails()))
                    .previewedWith(context);

            ImportResult result = batch.confirm(context, false, ids(1), TODAY, HALF_AN_HOUR_LATER);

            assertThat(result.orders().get(0).window()).isEqualTo(early.on(TOMORROW));
        }

        @Test
        void GivenACompanyDefaultWindowInTheSkippedHour_WhenPreviewed_ThenTheRowListsNonexistentLocalTime() {
            DeliveryDate springForward = DeliveryDate.of(LocalDate.of(2027, 3, 26));
            OrderDefaults earlyHours = new OrderDefaults(
                    DailyWindow.of(LocalTime.of(2, 30), LocalTime.of(4, 0)), Optional.of(OrdersTestData.FIVE_MINUTES));
            ImportContext context = ImportContext.of(
                    List.of(new ImportContext.AddressMatch(
                            NormalizedAddress.of(NEW_ADDRESS), new CustomerMatch.NoCustomer())),
                    List.of(emptyDate(springForward)),
                    earlyHours);

            ImportBatch batch = ImportBatch.preview(
                    BATCH,
                    HASH,
                    FileEncoding.UTF_8,
                    List.of(row(1, NEW_ADDRESS, springForward, bareDetails())),
                    context,
                    LocalDate.of(2027, 3, 1),
                    NOW);

            assertThat(batch.rows().get(0).errors())
                    .containsExactly(
                            new NonexistentLocalTime(LocalDate.of(2027, 3, 26).atTime(2, 30)));
        }

        @Test
        void GivenTwoRowsForOneNewAddress_WhenConfirmed_ThenOneCustomerReceivesBothOrders() {
            ImportRow sameAddressTypedOtherwise = row(2, Address.of("  הנביאים 10 ירושלים "), TOMORROW, bareDetails());
            ImportBatch batch = anImportBatch()
                    .withRows(cleanRow(1), sameAddressTypedOtherwise)
                    .previewed();

            ImportResult result = batch.confirm(context(), false, ids(3), TODAY, HALF_AN_HOUR_LATER);

            assertThat(result.customers()).hasSize(1);
            assertThat(result.orders())
                    .extracting(Order::customer)
                    .containsOnly(result.customers().get(0).id());
        }

        @Test
        void GivenTwoRowsForOneNewAddressNamingDifferentPeople_WhenConfirmed_ThenTheSecondOrderKeepsItsPhone() {
            ImportRow neighbour = new ImportRow.Parsed(
                    RowNumber.of(2),
                    new ImportLine(
                            NEW_ADDRESS,
                            Optional.of(ContactName.of("Avi Cohen")),
                            Optional.of(PhoneNumber.parse("054-2222222")),
                            TOMORROW,
                            bareDetails()));

            ImportResult result = anImportBatch()
                    .withRows(cleanRow(1), neighbour)
                    .previewed()
                    .confirm(context(), false, ids(3), TODAY, HALF_AN_HOUR_LATER);

            assertThat(result.customers()).hasSize(1);
            assertThat(result.orders()).extracting(Order::customer).containsOnly(CustomerId.of(fresh(1)));
            assertThat(result.orders().get(0).snapshot().recipient()).isEqualTo(Recipient.NONE);
            assertThat(result.orders().get(1).snapshot().recipient())
                    .isEqualTo(new Recipient(
                            Optional.of(ContactName.of("Avi Cohen")), Optional.of(PhoneNumber.parse("054-2222222"))));
        }

        @Test
        void GivenARowForAKnownCustomerUnderAnotherName_WhenConfirmed_ThenTheRowsNameAndPhoneAreTheRecipient() {
            ImportRow otherName = new ImportRow.Parsed(
                    RowNumber.of(1),
                    new ImportLine(
                            KNOWN_ADDRESS,
                            Optional.of(ContactName.of("אבי כהן")),
                            Optional.of(ImportBatchBuilder.PHONE),
                            TOMORROW,
                            details(TOMORROW).withRecipient(Recipient.NONE)));

            ImportResult result = anImportBatch()
                    .withRows(otherName)
                    .previewed()
                    .confirm(context(), false, ids(1), TODAY, HALF_AN_HOUR_LATER);

            assertThat(result.customers()).isEmpty();
            assertThat(result.orders().get(0).customer()).isEqualTo(KNOWN_CUSTOMER.id());
            assertThat(result.orders().get(0).snapshot().recipient())
                    .isEqualTo(new Recipient(
                            Optional.of(ContactName.of("אבי כהן")), Optional.of(PhoneNumber.parse("052-345-6780"))));
        }

        @Test
        void GivenARowForAKnownCustomerWithOnlyAnotherPhone_WhenConfirmed_ThenTheRowsNameAndPhoneAreTheRecipient() {
            ImportRow otherPhone = new ImportRow.Parsed(
                    RowNumber.of(1),
                    new ImportLine(
                            KNOWN_ADDRESS,
                            Optional.of(ImportBatchBuilder.NAME),
                            Optional.of(PhoneNumber.parse("054-222-2222")),
                            TOMORROW,
                            details(TOMORROW).withRecipient(Recipient.NONE)));

            ImportResult result = anImportBatch()
                    .withRows(otherPhone)
                    .previewed()
                    .confirm(context(), false, ids(1), TODAY, HALF_AN_HOUR_LATER);

            assertThat(result.orders().get(0).snapshot().recipient())
                    .isEqualTo(new Recipient(
                            Optional.of(ContactName.of("רות ישראלי")), Optional.of(PhoneNumber.parse("054-222-2222"))));
        }

        @Test
        void GivenARowForAKnownCustomerUnderAnotherNameAndNoPhone_WhenConfirmed_ThenOnlyTheNameIsTheRecipient() {
            ImportRow nameOnly = new ImportRow.Parsed(
                    RowNumber.of(1),
                    new ImportLine(
                            KNOWN_ADDRESS,
                            Optional.of(ContactName.of("אבי כהן")),
                            Optional.empty(),
                            TOMORROW,
                            details(TOMORROW).withRecipient(Recipient.NONE)));

            ImportResult result = anImportBatch()
                    .withRows(nameOnly)
                    .previewed()
                    .confirm(context(), false, ids(1), TODAY, HALF_AN_HOUR_LATER);

            assertThat(result.orders().get(0).snapshot().recipient())
                    .isEqualTo(new Recipient(Optional.of(ContactName.of("אבי כהן")), Optional.empty()));
        }

        @Test
        void GivenARowNamingAnotherPersonWithItsRecipientColumnsFilled_WhenConfirmed_ThenItsRecipientIsKeptAsGiven() {
            ImportRow ownRecipient = new ImportRow.Parsed(
                    RowNumber.of(1),
                    new ImportLine(
                            KNOWN_ADDRESS,
                            Optional.of(ContactName.of("אבי כהן")),
                            Optional.of(PhoneNumber.parse("054-222-2222")),
                            TOMORROW,
                            details(TOMORROW)));

            ImportResult result = anImportBatch()
                    .withRows(ownRecipient)
                    .previewed()
                    .confirm(context(), false, ids(1), TODAY, HALF_AN_HOUR_LATER);

            assertThat(result.orders().get(0).snapshot().recipient())
                    .isEqualTo(new Recipient(
                            Optional.of(ContactName.of("דנה לוי")), Optional.of(PhoneNumber.parse("052-345-6789"))));
        }

        static Stream<Arguments> rowsNamingTheCustomer() {
            return Stream.of(
                    Arguments.of(
                            "its own name and phone",
                            Optional.of(ImportBatchBuilder.NAME),
                            Optional.of(ImportBatchBuilder.PHONE)),
                    Arguments.of(
                            "its phone typed otherwise",
                            Optional.of(ImportBatchBuilder.NAME),
                            Optional.of(PhoneNumber.parse("+972523456780"))),
                    Arguments.of("no name and no phone", Optional.empty(), Optional.empty()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("rowsNamingTheCustomer")
        void GivenARowForAKnownCustomerNamingNoOneElse_WhenConfirmed_ThenTheOrderHasNoRecipientOfItsOwn(
                String given, Optional<ContactName> name, Optional<PhoneNumber> phone) {
            ImportRow sameContact = new ImportRow.Parsed(
                    RowNumber.of(1),
                    new ImportLine(
                            KNOWN_ADDRESS,
                            name,
                            phone,
                            TOMORROW,
                            details(TOMORROW).withRecipient(Recipient.NONE)));

            ImportResult result = anImportBatch()
                    .withRows(sameContact)
                    .previewed()
                    .confirm(context(), false, ids(1), TODAY, HALF_AN_HOUR_LATER);

            assertThat(result.orders().get(0).snapshot().recipient()).isEqualTo(Recipient.NONE);
        }

        @Test
        void GivenAWeightWithThreeDecimals_WhenConfirmed_ThenImportHasErrors() {
            DomainError threeDecimals = refusalOf(() -> Weight.kilograms(new BigDecimal("0.125")));
            ImportBatch batch = anImportBatch()
                    .withRows(
                            cleanRow(1), cleanRow(2), new ImportRow.Unreadable(RowNumber.of(3), List.of(threeDecimals)))
                    .previewed();

            assertAllRefusedUnchanged(
                    batch,
                    () -> batch.confirm(context(), false, ids(4), TODAY, HALF_AN_HOUR_LATER),
                    new ImportHasErrors(1),
                    List.of(new RowRefused(RowNumber.of(3), new QuantityOutOfRange(Measure.WEIGHT))));
        }

        @Test
        void GivenARowWhoseNameIsPastTheCap_WhenConfirmed_ThenItsRowRefusedCarriesTextTooLong() {
            DomainError tooLong = refusalOf(() -> ContactName.of("א".repeat(TextKind.NAME.maxLength() + 1)));
            ImportBatch batch = anImportBatch()
                    .withRows(cleanRow(1), new ImportRow.Unreadable(RowNumber.of(2), List.of(tooLong)))
                    .previewed();

            assertAllRefusedUnchanged(
                    batch,
                    () -> batch.confirm(context(), false, ids(3), TODAY, HALF_AN_HOUR_LATER),
                    new ImportHasErrors(1),
                    List.of(new RowRefused(RowNumber.of(2), new TextTooLong(TextKind.NAME, 100))));
        }

        @Test
        void
                GivenARowWhoseAddressHoldsABidiOverride_WhenConfirmed_ThenItsRowRefusedCarriesTextHasForbiddenCharacters() {
            String overridden = "הרצל" + (char) 0x202E + "5";
            DomainError forbidden = refusalOf(() -> Address.of(overridden));
            ImportBatch batch = anImportBatch()
                    .withRows(new ImportRow.Unreadable(RowNumber.of(4), List.of(forbidden)))
                    .previewed();

            assertAllRefusedUnchanged(
                    batch,
                    () -> batch.confirm(context(), false, ids(1), TODAY, HALF_AN_HOUR_LATER),
                    new ImportHasErrors(1),
                    List.of(new RowRefused(RowNumber.of(4), new TextHasForbiddenCharacters(TextKind.ADDRESS))));
        }

        @Test
        void GivenSeveralBadRowsInOneFile_WhenConfirmed_ThenRowRefused() {
            ImportRow nameless = new ImportRow.Parsed(
                    RowNumber.of(3),
                    new ImportLine(NEW_ADDRESS, Optional.empty(), Optional.empty(), YESTERDAY, bareDetails()));
            ImportBatch batch = anImportBatch()
                    .withRows(
                            rowWithReference(1, REFERENCE),
                            row(2, SHARED_ADDRESS, TOMORROW, bareDetails()),
                            nameless,
                            rowWithReference(4, REFERENCE),
                            row(5, KNOWN_ADDRESS, TOMORROW, bareDetails()))
                    .previewed();

            assertAllRefusedUnchanged(
                    batch,
                    () -> batch.confirm(
                            context(emptyDate(TOMORROW), emptyDate(YESTERDAY)),
                            false,
                            ids(9),
                            TODAY,
                            HALF_AN_HOUR_LATER),
                    new ImportHasErrors(4),
                    List.of(
                            new RowRefused(
                                    RowNumber.of(2), new AmbiguousAddress(List.of(customerId(2), customerId(3)))),
                            new RowRefused(RowNumber.of(3), new NewCustomerNeedsNameAndPhone()),
                            new RowRefused(RowNumber.of(3), new DateInPast(YESTERDAY)),
                            new RowRefused(RowNumber.of(4), new DuplicateOrderReference(TOMORROW)),
                            new RowRefused(RowNumber.of(5), new NoServiceTime())));
        }

        @Test
        void GivenAReferenceAnExistingOrderCarriesOnTheDate_WhenConfirmed_ThenItsRowIsRefused() {
            ImportBatch batch =
                    anImportBatch().withRows(rowWithReference(1, REFERENCE)).previewed();

            assertAllRefusedUnchanged(
                    batch,
                    () -> batch.confirm(
                            context(dateWithReference(TOMORROW, REFERENCE)), false, ids(2), TODAY, HALF_AN_HOUR_LATER),
                    new ImportHasErrors(1),
                    List.of(new RowRefused(RowNumber.of(1), new DuplicateOrderReference(TOMORROW))));
        }

        @Test
        void GivenADateWithRoomForOneMore_WhenTwoRowsAreConfirmedForIt_ThenTheSecondIsDateFull() {
            ImportBatch batch =
                    anImportBatch().withRows(cleanRow(1), cleanRow(2)).previewed();

            assertAllRefusedUnchanged(
                    batch,
                    () -> batch.confirm(context(dateWith(TOMORROW, 199)), false, ids(3), TODAY, HALF_AN_HOUR_LATER),
                    new ImportHasErrors(1),
                    List.of(new RowRefused(RowNumber.of(2), new DateFull(TOMORROW, 200))));
        }

        @Test
        void GivenACleanPreviewWhoseDateFilledSince_WhenConfirmed_ThenTheRowsAreCheckedAgain() {
            ImportBatch batch = anImportBatch().withRows(cleanRow(1)).previewed();

            assertAllRefusedUnchanged(
                    batch,
                    () -> batch.confirm(context(dateWith(TOMORROW, 200)), false, ids(2), TODAY, HALF_AN_HOUR_LATER),
                    new ImportHasErrors(1),
                    List.of(new RowRefused(RowNumber.of(1), new DateFull(TOMORROW, 200))));
        }

        @Test
        void GivenAPreviewWithAnErrorFixedSince_WhenConfirmed_ThenItIsConfirmedAndKeepsNoRow() {
            ImportBatch batch = anImportBatch()
                    .withRows(row(1, SHARED_ADDRESS, TOMORROW, details(TOMORROW)))
                    .previewed();
            ImportContext fixed = ImportContext.of(
                    List.of(new ImportContext.AddressMatch(
                            NormalizedAddress.of(SHARED_ADDRESS), new CustomerMatch.OneCustomer(KNOWN_CUSTOMER))),
                    List.of(emptyDate(TOMORROW)),
                    DEFAULTS);

            ImportResult result = batch.confirm(fixed, false, ids(1), TODAY, HALF_AN_HOUR_LATER);

            assertThat(result.orders()).extracting(Order::customer).containsExactly(KNOWN_CUSTOMER.id());
            assertThat(batch.state()).isEqualTo(new ImportBatch.State.Confirmed(HALF_AN_HOUR_LATER));
            assertThat(batch.rows()).isEmpty();
        }

        @Test
        void GivenAFileReadAsWindows1255_WhenConfirmedWithoutTheEncodingConfirmed_ThenEncodingNotConfirmed() {
            ImportBatch batch = anImportBatch().readAsWindows1255().previewed();

            assertRefusedUnchanged(
                    batch,
                    () -> batch.confirm(context(), false, ids(2), TODAY, HALF_AN_HOUR_LATER),
                    new EncodingNotConfirmed());
        }

        @Test
        void GivenAFileReadAsWindows1255_WhenConfirmedWithTheEncodingConfirmed_ThenItIsConfirmed() {
            ImportBatch batch = anImportBatch().readAsWindows1255().previewed();

            batch.confirm(context(), true, ids(2), TODAY, HALF_AN_HOUR_LATER);

            assertThat(batch.state()).isInstanceOf(ImportBatch.State.Confirmed.class);
        }

        @Test
        void GivenAPreviewOneHourOld_WhenConfirmed_ThenImportExpired() {
            ImportBatch batch = anImportBatch().previewed();

            assertRefusedUnchanged(
                    batch, () -> batch.confirm(context(), false, ids(2), TODAY, inMinutes(60)), new ImportExpired());
        }

        @Test
        void GivenAClockSteppedBackBeforeThePreview_WhenConfirmed_ThenItIsConfirmedAtThePreviewAndStillRestores() {
            ImportBatch batch = anImportBatch().previewed();

            ImportResult result = batch.confirm(context(), false, ids(2), TODAY, Instant.parse("2027-06-15T05:59:59Z"));

            assertThat(batch.state()).isEqualTo(new ImportBatch.State.Confirmed(Instant.parse("2027-06-15T06:00:00Z")));
            assertThat(ImportBatch.restore(batch.snapshot()).snapshot()).isEqualTo(batch.snapshot());
            assertThat(result.orders()).hasSize(1);
        }

        @Test
        void GivenAPreviewJustUnderAnHourOld_WhenConfirmed_ThenItIsConfirmed() {
            ImportBatch batch = anImportBatch().previewed();
            Instant justInTime = inMinutes(60).minusMillis(1);

            batch.confirm(context(), false, ids(2), TODAY, justInTime);

            assertThat(batch.state()).isEqualTo(new ImportBatch.State.Confirmed(justInTime));
        }
    }

    @Nested
    class RefusingOneThing {

        @Test
        void GivenARowForANewAddressWithoutAPhone_WhenConfirmed_ThenNewCustomerNeedsNameAndPhone() {
            ImportRow noPhone = new ImportRow.Parsed(
                    RowNumber.of(1),
                    new ImportLine(
                            NEW_ADDRESS,
                            Optional.of(ImportBatchBuilder.NAME),
                            Optional.empty(),
                            TOMORROW,
                            bareDetails()));
            ImportBatch batch = anImportBatch().withRows(noPhone).previewed();

            assertAllRefusedUnchanged(
                    batch,
                    () -> batch.confirm(context(), false, ids(2), TODAY, HALF_AN_HOUR_LATER),
                    new ImportHasErrors(1),
                    List.of(new RowRefused(RowNumber.of(1), new NewCustomerNeedsNameAndPhone())));
            assertThat(batch.rows().get(0).errors()).containsExactly(new NewCustomerNeedsNameAndPhone());
        }

        @Test
        void GivenAConfirmedBatch_WhenConfirmedAgain_ThenImportAlreadyConfirmed() {
            ImportBatch batch = anImportBatch().previewed();
            batch.confirm(context(), false, ids(2), TODAY, HALF_AN_HOUR_LATER);

            assertRefusedUnchanged(
                    batch,
                    () -> batch.confirm(context(), false, ids(2), TODAY, HALF_AN_HOUR_LATER),
                    new ImportAlreadyConfirmed());
        }

        @Test
        void GivenADiscardedBatch_WhenConfirmed_ThenImportDiscarded() {
            ImportBatch batch = anImportBatch().previewed();
            batch.discard(HALF_AN_HOUR_LATER);

            assertRefusedUnchanged(
                    batch,
                    () -> batch.confirm(context(), false, ids(2), TODAY, HALF_AN_HOUR_LATER),
                    new ImportDiscarded());
        }
    }

    @Nested
    class Ending {

        @Test
        void GivenAPreview_WhenDiscarded_ThenItKeepsNoRow() {
            ImportBatch batch = anImportBatch().previewed();

            batch.discard(HALF_AN_HOUR_LATER);

            assertThat(batch.state()).isEqualTo(new ImportBatch.State.Discarded(HALF_AN_HOUR_LATER));
            assertThat(batch.rows()).isEmpty();
        }

        @Test
        void GivenAClockSteppedBackBeforeThePreview_WhenDiscarded_ThenItIsDiscardedAtThePreviewAndStillRestores() {
            ImportBatch batch = anImportBatch().previewed();

            batch.discard(Instant.parse("2027-06-15T05:59:59Z"));

            assertThat(batch.state()).isEqualTo(new ImportBatch.State.Discarded(Instant.parse("2027-06-15T06:00:00Z")));
            assertThat(ImportBatch.restore(batch.snapshot()).snapshot()).isEqualTo(batch.snapshot());
        }

        @Test
        void GivenTheTimeOfThePreview_WhenDiscarded_ThenItIsDiscardedAtThatTime() {
            ImportBatch batch = anImportBatch().previewed();

            batch.discard(Instant.parse("2027-06-15T06:00:00Z"));

            assertThat(batch.state()).isEqualTo(new ImportBatch.State.Discarded(Instant.parse("2027-06-15T06:00:00Z")));
        }

        @Test
        void GivenAClockSteppedBackBeforeThePreview_WhenExpired_ThenImportNotYetExpired() {
            ImportBatch batch = anImportBatch().previewed();

            assertRefusedUnchanged(
                    batch, () -> batch.expire(Instant.parse("2027-06-15T05:59:59Z")), new ImportNotYetExpired());
        }

        @Test
        void GivenAPreviewAnHourOld_WhenExpired_ThenItKeepsNoRow() {
            ImportBatch batch = anImportBatch().previewed();

            batch.expire(inMinutes(60));

            assertThat(batch.state()).isEqualTo(new ImportBatch.State.Expired(inMinutes(60)));
            assertThat(batch.rows()).isEmpty();
        }

        @Test
        void GivenAPreviewUnderAnHourOld_WhenExpired_ThenImportNotYetExpired() {
            ImportBatch batch = anImportBatch().previewed();

            assertRefusedUnchanged(batch, () -> batch.expire(inMinutes(59)), new ImportNotYetExpired());
        }
    }

    /** Each state × each command: the command is taken, or refused with the named error. */
    @Nested
    class Lifecycle {

        enum Command {
            CONFIRM(
                    batch -> batch.confirm(context(), false, ids(2), TODAY, HALF_AN_HOUR_LATER),
                    new ImportBatch.State.Confirmed(HALF_AN_HOUR_LATER)),
            DISCARD(batch -> batch.discard(HALF_AN_HOUR_LATER), new ImportBatch.State.Discarded(HALF_AN_HOUR_LATER)),
            EXPIRE(batch -> batch.expire(inMinutes(90)), new ImportBatch.State.Expired(inMinutes(90)));

            private final Consumer<ImportBatch> call;
            private final ImportBatch.State result;

            Command(Consumer<ImportBatch> call, ImportBatch.State result) {
                this.call = call;
                this.result = result;
            }
        }

        static Stream<Arguments> matrix() {
            ImportBatch.State previewed = new ImportBatch.State.Previewed();
            ImportBatch.State confirmed = new ImportBatch.State.Confirmed(inMinutes(10));
            ImportBatch.State discarded = new ImportBatch.State.Discarded(inMinutes(10));
            ImportBatch.State expired = new ImportBatch.State.Expired(inMinutes(70));
            return Stream.of(
                    Arguments.of(previewed, Command.CONFIRM, null),
                    Arguments.of(previewed, Command.DISCARD, null),
                    Arguments.of(previewed, Command.EXPIRE, null),
                    Arguments.of(confirmed, Command.CONFIRM, new ImportAlreadyConfirmed()),
                    Arguments.of(confirmed, Command.DISCARD, new ImportAlreadyConfirmed()),
                    Arguments.of(confirmed, Command.EXPIRE, new ImportAlreadyConfirmed()),
                    Arguments.of(discarded, Command.CONFIRM, new ImportDiscarded()),
                    Arguments.of(discarded, Command.DISCARD, new ImportDiscarded()),
                    Arguments.of(discarded, Command.EXPIRE, new ImportDiscarded()),
                    Arguments.of(expired, Command.CONFIRM, new ImportExpired()),
                    Arguments.of(expired, Command.DISCARD, new ImportExpired()),
                    Arguments.of(expired, Command.EXPIRE, new ImportExpired()));
        }

        @ParameterizedTest(name = "{0} × {1} → {2}")
        @MethodSource("matrix")
        void GivenABatchInEachState_WhenEachCommandRuns_ThenItTakesItsResultOrIsRefusedByName(
                ImportBatch.State state, Command command, @Nullable OrdersError expected) {
            ImportBatch batch = anImportBatch().inState(state).restored();

            if (expected == null) {
                command.call.accept(batch);
                assertThat(batch.state()).isEqualTo(command.result);
                assertThat(batch.rows()).isEmpty();
                assertThat(batch.pendingEvents()).isEmpty();
            } else {
                assertRefusedUnchanged(batch, () -> command.call.accept(batch), expected);
            }
        }
    }

    @Nested
    class Restoring {

        static Stream<Arguments> builderStates() {
            return Stream.of(
                    Arguments.of("previewed", anImportBatch().previewed()),
                    Arguments.of(
                            "previewed with errors",
                            anImportBatch()
                                    .withRows(row(1, SHARED_ADDRESS, TOMORROW, bareDetails()))
                                    .previewed()),
                    Arguments.of(
                            "confirmed",
                            anImportBatch()
                                    .inState(new ImportBatch.State.Confirmed(HALF_AN_HOUR_LATER))
                                    .restored()),
                    Arguments.of(
                            "discarded",
                            anImportBatch()
                                    .inState(new ImportBatch.State.Discarded(HALF_AN_HOUR_LATER))
                                    .restored()),
                    Arguments.of(
                            "expired",
                            anImportBatch()
                                    .inState(new ImportBatch.State.Expired(inMinutes(60)))
                                    .restored()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("builderStates")
        void GivenABatchInEachBuilderState_WhenRestoredFromItsSnapshot_ThenItIsTheSame(
                String state, ImportBatch batch) {
            assertThat(ImportBatch.restore(batch.snapshot()).snapshot()).isEqualTo(batch.snapshot());
        }

        private static ImportBatchSnapshot with(List<PreviewRow> rows, Instant previewedAt, ImportBatch.State state) {
            return new ImportBatchSnapshot(BATCH, HASH, FileEncoding.UTF_8, rows, previewedAt, state, Version.of(1));
        }

        private static void assertInvalid(ImportBatchSnapshot snapshot, String detail) {
            assertThatThrownBy(() -> ImportBatch.restore(snapshot))
                    .isInstanceOfSatisfying(
                            StoredStateInvalid.class,
                            invalid -> assertThat(invalid.detail()).isEqualTo(detail));
        }

        static Stream<Arguments> endedStates() {
            return Stream.of(
                    Arguments.of(new ImportBatch.State.Confirmed(HALF_AN_HOUR_LATER)),
                    Arguments.of(new ImportBatch.State.Discarded(HALF_AN_HOUR_LATER)),
                    Arguments.of(new ImportBatch.State.Expired(inMinutes(60))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("endedStates")
        void GivenAStoredEndedBatchWithARow_WhenRestored_ThenStoredStateInvalid(ImportBatch.State ended) {
            assertInvalid(
                    with(List.of(new PreviewRow(cleanRow(1), List.of())), NOW, ended),
                    "a confirmed, discarded or expired import keeps a row");
        }

        static Stream<Arguments> rowsOutOfOrder() {
            return Stream.of(Arguments.of(2, 2), Arguments.of(3, 1));
        }

        @ParameterizedTest(name = "rows {0}, then {1}")
        @MethodSource("rowsOutOfOrder")
        void GivenStoredRowsNotUniqueAndAscending_WhenRestored_ThenStoredStateInvalid(int first, int second) {
            assertInvalid(
                    with(
                            List.of(
                                    new PreviewRow(cleanRow(first), List.of()),
                                    new PreviewRow(cleanRow(second), List.of())),
                            NOW,
                            new ImportBatch.State.Previewed()),
                    "the row numbers are not unique and ascending");
        }

        @Test
        void GivenAStoredStateOlderThanThePreview_WhenRestored_ThenStoredStateInvalid() {
            assertInvalid(
                    with(List.of(), NOW, new ImportBatch.State.Discarded(NOW.minusSeconds(1))),
                    "the import's state is older than its preview");
        }

        @Test
        void GivenAStoredConfirmationAfterTheHour_WhenRestored_ThenStoredStateInvalid() {
            assertInvalid(
                    with(List.of(), NOW, new ImportBatch.State.Confirmed(inMinutes(60))),
                    "the import was confirmed after its hour");
        }

        @Test
        void GivenAStoredUnreadableRowWithNoError_WhenRebuilt_ThenStoredStateInvalid() {
            assertThatThrownBy(() -> new ImportRow.Unreadable(RowNumber.of(1), List.of()))
                    .isInstanceOf(StoredStateInvalid.class);
        }

        @Test
        void GivenAStoredRowNumberOfZero_WhenRebuilt_ThenStoredStateInvalid() {
            assertThatThrownBy(() -> RowNumber.of(0)).isInstanceOf(StoredStateInvalid.class);
        }
    }

    private static DomainError refusalOf(ThrowingCallable call) {
        try {
            call.call();
        } catch (RuleViolation refused) {
            return refused.error();
        } catch (Throwable unexpected) {
            throw new AssertionError("expected a refusal", unexpected);
        }
        throw new AssertionError("expected a refusal, but the call succeeded");
    }
}
