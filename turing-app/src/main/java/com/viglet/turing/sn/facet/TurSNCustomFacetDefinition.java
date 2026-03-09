package com.viglet.turing.sn.facet;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;

public class TurSNCustomFacetDefinition implements TurSNFacetDefinition {
    private final TurSNSiteFieldExt fieldExt;
    private final TurSNSiteCustomFacet customFacet;
    private final Locale locale;

    public TurSNCustomFacetDefinition(TurSNSiteFieldExt fieldExt,
            TurSNSiteCustomFacet customFacet, Locale locale) {
        this.fieldExt = fieldExt;
        this.customFacet = customFacet;
        this.locale = locale;
    }

    @Override
    public String getId() {
        return customFacet.getId();
    }

    @Override
    public String getName() {
        return customFacet.getName();
    }

    @Override
    public String getLabel() {
        return Optional.ofNullable(customFacet.getDefaultLabel())
                .filter(TurSNCustomFacetDefinition::hasText)
                .orElseGet(() -> firstAvailableLabel(customFacet.getLabel())
                        .orElse(customFacet.getName()));
    }

    @Override
    public Integer getPosition() {
        return Optional.ofNullable(customFacet.getFacetPosition())
                .filter(position -> position > 0)
                .orElse(Integer.MAX_VALUE);
    }

    @Override
    public TurSNSiteFacetFieldEnum getFacetType() {
        return Optional.ofNullable(customFacet.getFacetType()).orElse(TurSNSiteFacetFieldEnum.DEFAULT);
    }

    @Override
    public TurSNSiteFacetFieldEnum getFacetItemType() {
        return Optional.ofNullable(customFacet.getFacetItemType())
                .orElse(TurSNSiteFacetFieldEnum.DEFAULT);
    }

    @Override
    public TurSNSiteFieldExt getFieldExt() {
        return fieldExt;
    }

    @Override
    public Set<TurSNSiteFieldExtFacet> getFacetLocales() {
        Set<TurSNSiteFieldExtFacet> locales = new HashSet<>();
        Optional.ofNullable(customFacet.getLabel()).ifPresent(labels -> labels.forEach((localeKey, label) -> {
            if (hasText(label)) {
                locales.add(TurSNSiteFieldExtFacet.builder()
                        .locale(Locale.forLanguageTag(localeKey))
                        .label(label)
                        .build());
            }
        }));
        if (locales.isEmpty()) {
            locales.add(TurSNSiteFieldExtFacet.builder()
                    .locale(Optional.ofNullable(locale).orElse(Locale.ROOT))
                    .label(getLabel())
                    .build());
        }
        return locales;
    }

    @Override
    public boolean isCustomFacet() {
        return true;
    }

    @Override
    public Set<TurSNSiteCustomFacetItem> getItems() {
        return Optional.ofNullable(customFacet.getItems()).orElse(Collections.emptySet());
    }

    private static Optional<String> firstAvailableLabel(Map<String, String> labels) {
        return Optional.ofNullable(labels)
                .map(Map::entrySet)
                .stream()
                .flatMap(Set::stream)
                .sorted(Map.Entry.comparingByKey(Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(Map.Entry::getValue)
                .filter(TurSNCustomFacetDefinition::hasText)
                .findFirst();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}