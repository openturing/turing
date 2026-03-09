package com.viglet.turing.client.sn.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TurSNJobItemsTest {

    @Test
    void shouldSupportAddRemoveSizeAndClearOperations() {
        TurSNJobItem first = new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"));
        TurSNJobItem second = new TurSNJobItem(TurSNJobAction.DELETE, List.of("Sample"));

        TurSNJobItems items = new TurSNJobItems();
        items.add(first);
        items.add(second);

        assertThat(items.size()).isEqualTo(2);
        assertThat(items.getTuringDocuments()).containsExactly(first, second);

        items.remove(first);
        assertThat(items.size()).isEqualTo(1);

        items.clear();
        assertThat(items.size()).isZero();
    }

    @Test
    void shouldBuildFromSingleItemAndListAndRenderToString() {
        TurSNJobItem item = new TurSNJobItem(TurSNJobAction.COMMIT, List.of("Sample"));

        TurSNJobItems single = new TurSNJobItems(item);
        assertThat(single.size()).isEqualTo(1);

        TurSNJobItems listCtor = new TurSNJobItems(List.of(item));
        assertThat(listCtor.toString()).contains("turSNJobItem:");
    }
}
