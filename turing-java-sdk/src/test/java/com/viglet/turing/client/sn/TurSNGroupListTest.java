package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TurSNGroupListTest {

    @Test
    void shouldExposeGroupsAndIterator() {
        TurSNGroupList groupList = new TurSNGroupList();
        TurSNGroup g1 = new TurSNGroup();
        TurSNGroup g2 = new TurSNGroup();

        groupList.setTurSNGroups(List.of(g1, g2));

        assertThat(groupList.getTurSNGroups()).containsExactly(g1, g2);
        assertThat(groupList).containsExactly(g1, g2);
    }
}
