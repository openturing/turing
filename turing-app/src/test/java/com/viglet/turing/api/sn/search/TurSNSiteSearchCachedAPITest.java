package com.viglet.turing.api.sn.search;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.sn.TurSNSearchProcess;

@ExtendWith(MockitoExtension.class)
class TurSNSiteSearchCachedAPITest {

    @Mock
    private TurSNSearchProcess turSNSearchProcess;

    private TurSNSiteSearchCachedAPI api;

    @BeforeEach
    void setUp() {
        api = new TurSNSiteSearchCachedAPI(
                turSNSearchProcess,
                false, // enabled
                "mongodb://localhost:27017",
                "turingLog",
                "server",
                "indexing",
                "aem",
                30 // purgeDays
        );
    }

    @Test
    void testCleanSearchCache() {
        assertDoesNotThrow(() -> api.cleanSearchCache());
    }

    @Test
    void testSearchCached() {
        String cacheKey = "key1";
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site1", null, null, null, null);
        TurSNSiteSearchBean expectedBean = new TurSNSiteSearchBean();

        when(turSNSearchProcess.search(context)).thenReturn(expectedBean);

        TurSNSiteSearchBean result = api.searchCached(cacheKey, context);

        assertEquals(expectedBean, result);
        verify(turSNSearchProcess, times(1)).search(context);
    }

    @Test
    void testPurgeMongoDBLogs_Disabled() {
        assertDoesNotThrow(() -> api.purgeMongoDBLogs());
        // Should return early due to enabled = false
        // Static MongoClients.create won't be called, throwing no exceptions.
    }
}
