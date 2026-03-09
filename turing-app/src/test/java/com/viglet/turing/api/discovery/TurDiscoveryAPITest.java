package com.viglet.turing.api.discovery;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.viglet.turing.api.TurAPIBean;
import com.viglet.turing.properties.TurConfigProperties;

@ExtendWith(MockitoExtension.class)
class TurDiscoveryAPITest {

    @Mock
    private TurAPIBean turAPIBean;

    @Mock
    private TurConfigProperties turConfigProperties;

    @InjectMocks
    private TurDiscoveryAPI turDiscoveryAPI;

    @Test
    void testInfo() throws Exception {
        when(turConfigProperties.isKeycloak()).thenReturn(true);
        when(turConfigProperties.isMultiTenant()).thenReturn(false);

        TurAPIBean realApiBean = new TurAPIBean();
        TurDiscoveryAPI realController = new TurDiscoveryAPI(realApiBean, turConfigProperties);
        MockMvc realMockMvc = MockMvcBuilders.standaloneSetup(realController).build();

        realMockMvc.perform(get("/api/discovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value("Viglet Turing"))
                .andExpect(jsonPath("$.keycloak").value(true))
                .andExpect(jsonPath("$.multiTenant").value(false));

        verify(turConfigProperties, times(1)).isKeycloak();
        verify(turConfigProperties, times(1)).isMultiTenant();
    }
}
