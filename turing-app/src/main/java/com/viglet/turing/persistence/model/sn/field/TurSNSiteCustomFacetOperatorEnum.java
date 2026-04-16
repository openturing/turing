package com.viglet.turing.persistence.model.sn.field;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Operator types for custom facet items.
 *
 * <p>Semantics (as mapped to Solr range syntax):
 * <ul>
 *   <li>{@link #EQUAL} — exact match ({@code field:value})</li>
 *   <li>{@link #GREATER_THAN} — strict greater ({@code field:{start TO *]})</li>
 *   <li>{@link #GREATER_THAN_OR_EQUAL} — inclusive greater ({@code field:[start TO *]})</li>
 *   <li>{@link #LESS_THAN} — strict less ({@code field:[* TO end\u007D})</li>
 *   <li>{@link #LESS_THAN_OR_EQUAL} — inclusive less ({@code field:[* TO end]})</li>
 *   <li>{@link #BETWEEN_INCLUSIVE} — closed range ({@code field:[start TO end]})</li>
 *   <li>{@link #BETWEEN_EXCLUSIVE} — open range ({@code field:{start TO end\u007D})</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
public enum TurSNSiteCustomFacetOperatorEnum {
    EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    @JsonAlias("BETWEEN")
    BETWEEN_INCLUSIVE,
    BETWEEN_EXCLUSIVE
}
