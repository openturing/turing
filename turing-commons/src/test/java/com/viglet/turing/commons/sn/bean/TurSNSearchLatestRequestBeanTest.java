package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNSearchLatestRequestBeanTest {

    @Test
    void shouldStoreUserId() {
        TurSNSearchLatestRequestBean bean = new TurSNSearchLatestRequestBean();
        bean.setUserId("user-1");

        assertThat(bean.getUserId()).isEqualTo("user-1");
    }
}
