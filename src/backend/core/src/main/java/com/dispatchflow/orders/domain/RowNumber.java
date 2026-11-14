package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.StoredStateInvalid;

/**
 * A row's number in an import file, from 1, as the dispatcher sees it in the preview and in each
 * refusal (FR-203). The file reader numbers the rows, so a number below 1 is a reader bug or a
 * damaged stored row, never a user's mistake.
 *
 * @param value the row's number, 1 or more
 */
public record RowNumber(int value) implements Comparable<RowNumber> {

    private static final int FIRST = 1;

    /**
     * Checks the number.
     *
     * @throws StoredStateInvalid when the number is below 1
     */
    public RowNumber {
        if (value < FIRST) {
            throw new StoredStateInvalid(RowNumber.class, "a row number is 1 or more");
        }
    }

    /**
     * Creates the number.
     *
     * @param value the row's number, 1 or more
     * @return the number
     * @throws StoredStateInvalid when the number is below 1
     */
    public static RowNumber of(int value) {
        return new RowNumber(value);
    }

    @Override
    public int compareTo(RowNumber other) {
        return Integer.compare(value, other.value);
    }
}
