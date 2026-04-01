package com.viglet.turing.persistence.model.sn.searchrule;

/**
 * Types of actions that a search rule can apply.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public enum TurSNSiteSearchRuleActionTypeEnum {
    SET_SORT,
    ADD_FACETS,
    REMOVE_FACETS,
    SET_ROWS,
    ADD_FILTER_QUERY,
    SET_BOOST_QUERY
}
