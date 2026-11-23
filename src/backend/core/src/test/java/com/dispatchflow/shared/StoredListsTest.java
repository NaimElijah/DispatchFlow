package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoredListsTest {

    @Test
    void GivenNoList_WhenCheckedForAGap_ThenThereIsNone() {
        assertThat(StoredLists.anyHasGap()).isFalse();
    }

    @Test
    void GivenEmptyLists_WhenCheckedForAGap_ThenThereIsNone() {
        assertThat(StoredLists.anyHasGap(List.of(), new ArrayList<>())).isFalse();
    }

    @Test
    void GivenListsThatRefuseANullQuery_WhenCheckedForAGap_ThenThereIsNone() {
        assertThat(StoredLists.anyHasGap(List.of(1, 2), List.of("a"))).isFalse();
    }

    @Test
    void GivenAListWithAMissingFirstElement_WhenCheckedForAGap_ThenThereIsOne() {
        assertThat(StoredLists.anyHasGap(Arrays.asList(null, "b"))).isTrue();
    }

    @Test
    void GivenOnlyALaterListWithAMissingElement_WhenCheckedForAGap_ThenThereIsOne() {
        assertThat(StoredLists.anyHasGap(List.of(1, 2), Arrays.asList("a", null)))
                .isTrue();
    }
}
