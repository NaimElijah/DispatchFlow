package com.dispatchflow.shared;

import java.util.List;

/**
 * The check every root's load check runs first on the ordered lists it holds (ADR-128 decision 4).
 *
 * <p>Hibernate rebuilds a list mapped with an {@code @OrderColumn} by its stored positions, and
 * fills every position no row holds with {@code null}. The schema keeps each position unique but
 * not contiguous, so a damaged row can leave such a gap, and the first code that reads the element
 * then fails with a {@link NullPointerException} instead of {@link StoredStateInvalid}. A load
 * check that asks this first, before it reads any element, refuses the gap as damaged state.
 *
 * <p>It can't see every damage to a list's positions. The schema refuses a negative one, which
 * Hibernate would fail on before any load check runs. A lost last element just leaves a shorter
 * list, and a huge position makes Hibernate pad the list until memory runs out.
 */
public final class StoredLists {

    /** The detail of the {@link StoredStateInvalid} a load check throws for a gap. */
    public static final String GAP = "a stored list has a gap in its positions";

    private StoredLists() {}

    /**
     * Whether any of the lists, as Hibernate loaded them, misses an element at some position.
     *
     * <p>It walks each list rather than asking {@code contains(null)}, which an immutable list such
     * as {@code List.of} refuses.
     *
     * @param lists the ordered lists to check
     * @return {@code true} when one of them holds a {@code null} element
     */
    public static boolean anyHasGap(List<?>... lists) {
        for (List<?> list : lists) {
            for (Object element : list) {
                if (element == null) {
                    return true;
                }
            }
        }
        return false;
    }
}
