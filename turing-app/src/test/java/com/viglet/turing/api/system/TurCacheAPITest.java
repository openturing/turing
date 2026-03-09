package com.viglet.turing.api.system;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TurCacheAPITest {

    private MockMvc mockMvc;

    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cacheA;
    @Mock
    private Cache cacheB;

    @InjectMocks
    private TurCacheAPI api;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test
    void shouldListCachesSorted() throws Exception {
        when(cacheManager.getCacheNames()).thenReturn(List.of("z-cache", "a-cache", "m-cache"));

        mockMvc.perform(get("/api/system/cache"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.totalCaches").value(3))
                .andExpect(jsonPath("$.cacheNames[0]").value("a-cache"))
                .andExpect(jsonPath("$.cacheNames[1]").value("m-cache"))
                .andExpect(jsonPath("$.cacheNames[2]").value("z-cache"));
    }

    @Test
    void shouldClearExistingCachesAndIgnoreMissingOnes() throws Exception {
        when(cacheManager.getCacheNames()).thenReturn(List.of("b-cache", "a-cache", "missing-cache"));
        when(cacheManager.getCache("a-cache")).thenReturn(cacheA);
        when(cacheManager.getCache("b-cache")).thenReturn(cacheB);
        when(cacheManager.getCache("missing-cache")).thenReturn(null);

        mockMvc.perform(post("/api/system/cache/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.clearedCaches").value(3))
                .andExpect(jsonPath("$.cacheNames[0]").value("a-cache"))
                .andExpect(jsonPath("$.cacheNames[1]").value("b-cache"))
                .andExpect(jsonPath("$.cacheNames[2]").value("missing-cache"));

        verify(cacheA).clear();
        verify(cacheB).clear();
        verify(cacheManager).getCache("missing-cache");
    }

    @Test
    void shouldReturnSuccessWhenThereAreNoCaches() throws Exception {
        when(cacheManager.getCacheNames()).thenReturn(List.of());

        mockMvc.perform(post("/api/system/cache/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.clearedCaches").value(0));

        verify(cacheManager, never()).getCache(org.mockito.ArgumentMatchers.anyString());
    }
}
