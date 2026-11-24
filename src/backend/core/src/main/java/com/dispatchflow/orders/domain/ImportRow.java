package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StoredStateInvalid;
import java.util.List;
import java.util.Objects;

/**
 * One row of an import file as the file reader parsed it (ADR-086): either every field read, or the
 * refusal of each field it could not read. The reader is the CSV import's adapter; this context
 * receives its rows and never reads bytes.
 */
public sealed interface ImportRow extends PersonalData {

    /**
     * The row's number in the file.
     *
     * @return the number, from 1
     */
    RowNumber number();

    /**
     * A row whose fields were all read.
     *
     * @param number the row's number
     * @param line its fields
     */
    record Parsed(RowNumber number, ImportLine line) implements ImportRow {

        /** Checks both parts are given. */
        public Parsed {
            Objects.requireNonNull(number, "number");
            Objects.requireNonNull(line, "line");
        }

        /**
         * A form that hides the row, because it holds personal data.
         *
         * @return {@code ImportRow.Parsed[redacted]}
         */
        @Override
        public String toString() {
            return "ImportRow.Parsed[redacted]";
        }
    }

    /**
     * A row with fields the reader refused, such as a weight with more than two decimals, which the
     * kernel's {@code Weight} refuses with {@code QuantityOutOfRange} (ADR-102).
     *
     * @param number the row's number
     * @param fieldErrors each field's refusal, in column order; never empty
     */
    record Unreadable(RowNumber number, List<DomainError> fieldErrors) implements ImportRow {

        /**
         * Copies the refusals.
         *
         * @throws StoredStateInvalid when there are none: a row with no refusal was read, so an empty
         *     list is a reader bug or a damaged stored row, never a user's mistake
         */
        public Unreadable {
            Objects.requireNonNull(number, "number");
            fieldErrors = List.copyOf(fieldErrors);
            if (fieldErrors.isEmpty()) {
                throw new StoredStateInvalid(ImportRow.class, "an unreadable row names no refused field");
            }
        }

        /**
         * A form that hides the row, because it holds personal data.
         *
         * @return {@code ImportRow.Unreadable[redacted]}
         */
        @Override
        public String toString() {
            return "ImportRow.Unreadable[redacted]";
        }
    }
}
