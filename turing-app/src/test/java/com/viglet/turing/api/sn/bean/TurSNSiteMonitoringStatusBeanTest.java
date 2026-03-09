package com.viglet.turing.api.sn.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurSNSiteMonitoringStatusBeanTest {

    @Test
    void testGettersAndSetters() {
        TurSNSiteMonitoringStatusBean bean = new TurSNSiteMonitoringStatusBean();

        bean.setQueue(10);
        bean.setDocuments(500);

        assertEquals(10, bean.getQueue());
        assertEquals(500, bean.getDocuments());
    }
}
