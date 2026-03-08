package com.viglet.turing.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.sn.TurSNFieldType;

class TurSNFacetDefinitionTest {

    @Test
    void shouldExposeDefaultFlagsAndItems() {
        StubFacetDefinition definition = new StubFacetDefinition(baseField(), Set.of(), Set.of());

        assertThat(definition.isCustomFacet()).isFalse();
        assertThat(definition.getItems()).isEmpty();
        assertThat(definition.getSecondaryFacet()).isTrue();
        assertThat(definition.getShowAllFacetItems()).isFalse();
    }

    @Test
    void shouldMapToFacetOrderingFieldExt() {
        TurSNSiteFieldExt source = baseField();
        StubFacetDefinition definition = new StubFacetDefinition(source, Set.of(), Set.of());

        TurSNSiteFieldExt mapped = definition.toFacetOrderingFieldExt();

        assertThat(mapped.getId()).isEqualTo("facet-id");
        assertThat(mapped.getName()).isEqualTo("facet_name");
        assertThat(mapped.getFacetName()).isEqualTo("Facet Label");
        assertThat(mapped.getFacetPosition()).isEqualTo(3);
        assertThat(mapped.getFacet()).isEqualTo(1);
        assertThat(mapped.getEnabled()).isEqualTo(1);
        assertThat(mapped.getTurSNSite()).isSameAs(source.getTurSNSite());
        assertThat(mapped.getType()).isEqualTo(source.getType());
        assertThat(mapped.getSnType()).isEqualTo(source.getSnType());
        assertThat(mapped.getSecondaryFacet()).isEqualTo(source.getSecondaryFacet());
        assertThat(mapped.getShowAllFacetItems()).isEqualTo(source.getShowAllFacetItems());
    }

    @Test
    void shouldMapToFacetFieldExtDto() {
        TurSNSiteFieldExtFacet localeFacet = TurSNSiteFieldExtFacet.builder()
                .locale(Locale.US)
                .label("Facet Label")
                .build();
        StubFacetDefinition definition = new StubFacetDefinition(baseField(), Set.of(localeFacet), Set.of());

        TurSNSiteFieldExtDto dto = definition.toFacetFieldExtDto();

        assertThat(dto.getName()).isEqualTo("facet_name");
        assertThat(dto.getFacetName()).isEqualTo("Facet Label");
        assertThat(dto.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        assertThat(dto.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.OR);
        assertThat(dto.getSecondaryFacet()).isTrue();
        assertThat(dto.getShowAllFacetItems()).isFalse();
        assertThat(dto.getFacetLocales()).hasSize(1);
        assertThat(dto.getFacetLocales().iterator().next().getLocale()).isEqualTo(Locale.US);
    }

    private TurSNSiteFieldExt baseField() {
        TurSNSite site = new TurSNSite();
        return TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("price")
                .snType(TurSNFieldType.SE)
                .type(TurSEFieldType.STRING)
                .secondaryFacet(true)
                .showAllFacetItems(false)
                .turSNSite(site)
                .build();
    }

    private static class StubFacetDefinition implements TurSNFacetDefinition {
        private final TurSNSiteFieldExt fieldExt;
        private final Set<TurSNSiteFieldExtFacet> facetLocales;
        private final Set<TurSNSiteCustomFacetItem> items;

        private StubFacetDefinition(TurSNSiteFieldExt fieldExt,
                Set<TurSNSiteFieldExtFacet> facetLocales,
                Set<TurSNSiteCustomFacetItem> items) {
            this.fieldExt = fieldExt;
            this.facetLocales = facetLocales;
            this.items = items;
        }

        @Override
        public String getId() {
            return "facet-id";
        }

        @Override
        public String getName() {
            return "facet_name";
        }

        @Override
        public String getLabel() {
            return "Facet Label";
        }

        @Override
        public Integer getPosition() {
            return 3;
        }

        @Override
        public TurSNSiteFacetFieldEnum getFacetType() {
            return TurSNSiteFacetFieldEnum.AND;
        }

        @Override
        public TurSNSiteFacetFieldEnum getFacetItemType() {
            return TurSNSiteFacetFieldEnum.OR;
        }

        @Override
        public TurSNSiteFieldExt getFieldExt() {
            return fieldExt;
        }

        @Override
        public Set<TurSNSiteFieldExtFacet> getFacetLocales() {
            return facetLocales;
        }

        @Override
        public Set<TurSNSiteCustomFacetItem> getItems() {
            return items == null ? Collections.emptySet() : items;
        }
    }
}
