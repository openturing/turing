package com.viglet.turing.persistence.model.sn.merge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;

@ExtendWith(MockitoExtension.class)
class TurSNSiteMergeProvidersTest {

    @Mock
    private TurSNSite turSNSite;

    private TurSNSiteMergeProviders providers;

    @BeforeEach
    void setUp() {
        providers = new TurSNSiteMergeProviders();
    }

    @Test
    void shouldSetAndGetAllProperties() {
        providers.setId("merge-id");
        providers.setTurSNSite(turSNSite);
        providers.setLocale(Locale.US);
        providers.setDescription("Provider merge");
        providers.setProviderFrom("provider-a");
        providers.setProviderTo("provider-b");
        providers.setRelationFrom("child");
        providers.setRelationTo("parent");

        assertThat(providers.getId()).isEqualTo("merge-id");
        assertThat(providers.getTurSNSite()).isEqualTo(turSNSite);
        assertThat(providers.getLocale()).isEqualTo(Locale.US);
        assertThat(providers.getDescription()).isEqualTo("Provider merge");
        assertThat(providers.getProviderFrom()).isEqualTo("provider-a");
        assertThat(providers.getProviderTo()).isEqualTo("provider-b");
        assertThat(providers.getRelationFrom()).isEqualTo("child");
        assertThat(providers.getRelationTo()).isEqualTo("parent");
    }

    @Test
    void shouldInitializeOverwrittenFieldsAsEmptySet() {
        assertThat(providers.getOverwrittenFields()).isNotNull().isEmpty();
    }

    @Test
    void shouldReplaceOverwrittenFieldsSet() {
        TurSNSiteMergeProvidersField first = new TurSNSiteMergeProvidersField();
        first.setName("title");
        TurSNSiteMergeProvidersField second = new TurSNSiteMergeProvidersField();
        second.setName("description");
        Set<TurSNSiteMergeProvidersField> overwrittenFields = new HashSet<>();
        overwrittenFields.add(first);
        overwrittenFields.add(second);

        providers.setOverwrittenFields(overwrittenFields);

        assertThat(providers.getOverwrittenFields())
                .hasSize(2)
                .extracting(TurSNSiteMergeProvidersField::getName)
                .containsExactlyInAnyOrder("title", "description");
    }
}
