package com.dispatchflow.shared.adapter.out.uuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.FreshIds;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    private static final int UUID_VERSION_7 = 7;

    private final UuidV7Generator generator = new UuidV7Generator();

    @Test
    void GivenACountOfThree_WhenFreshIsAsked_ThenThreeDistinctVersion7Ids() {
        FreshIds ids = generator.fresh(3);

        Set<UUID> drawn = new HashSet<>();
        while (ids.remaining() > 0) {
            drawn.add(ids.next());
        }
        assertThat(drawn).hasSize(3).allSatisfy(id -> assertThat(id.version()).isEqualTo(UUID_VERSION_7));
    }

    @Test
    void GivenZero_WhenFreshIsAsked_ThenAnEmptySupply() {
        assertThat(generator.fresh(0).remaining()).isZero();
    }

    @Test
    void GivenANegativeCount_WhenFreshIsAsked_ThenIllegalArgumentException() {
        assertThatThrownBy(() -> generator.fresh(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("count is negative: -1");
    }

    @Test
    void GivenTheGenerator_WhenNextIsAsked_ThenAVersion7Id() {
        assertThat(generator.next().version()).isEqualTo(UUID_VERSION_7);
    }
}
