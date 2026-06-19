package com.viglet.turing.sn.searchrule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRule;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleAction;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleActionTypeEnum;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleCondition;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleLogicOperatorEnum;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleOperatorEnum;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleParameterEnum;
import com.viglet.turing.persistence.repository.sn.searchrule.TurSNSiteSearchRuleRepository;

/**
 * Evaluates search rules for a Semantic Navigation site and applies
 * matching rule actions to modify search parameters before query execution.
 * <p>
 * Rules are evaluated in position order. The first rule whose conditions
 * all match is applied (first-match semantics).
 * <p>
 * Conditions are grouped by parameter type. Within each group, conditions
 * are combined using the logicOperator (AND/OR). Between groups, AND is used.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Component
public class TurSNSearchRuleEvaluator {
    private static final Logger log = LoggerFactory.getLogger(TurSNSearchRuleEvaluator.class);

    private final TurSNSiteSearchRuleRepository searchRuleRepository;

    public TurSNSearchRuleEvaluator(TurSNSiteSearchRuleRepository searchRuleRepository) {
        this.searchRuleRepository = searchRuleRepository;
    }

    public void applyRules(TurSNSite turSNSite, TurSEParameters turSEParameters, Locale locale) {
        List<TurSNSiteSearchRule> enabledRules = searchRuleRepository.findEnabledByTurSNSite(turSNSite);
        for (TurSNSiteSearchRule rule : enabledRules) {
            if (allGroupsMatch(rule, turSEParameters, locale)) {
                log.debug("Search rule '{}' matched for site '{}'", rule.getName(), turSNSite.getName());
                applyActions(rule, turSEParameters);
                return;
            }
        }
    }

    private boolean allGroupsMatch(TurSNSiteSearchRule rule, TurSEParameters params, Locale locale) {
        if (rule.getConditions() == null || rule.getConditions().isEmpty()) {
            return false;
        }
        Map<TurSNSiteSearchRuleParameterEnum, List<TurSNSiteSearchRuleCondition>> groups =
                groupByParameter(rule.getConditions());
        return groups.values().stream()
                .allMatch(group -> evaluateGroup(group, params, locale));
    }

    private Map<TurSNSiteSearchRuleParameterEnum, List<TurSNSiteSearchRuleCondition>> groupByParameter(
            Set<TurSNSiteSearchRuleCondition> conditions) {
        Map<TurSNSiteSearchRuleParameterEnum, List<TurSNSiteSearchRuleCondition>> groups = new LinkedHashMap<>();
        for (TurSNSiteSearchRuleCondition condition : conditions) {
            groups.computeIfAbsent(condition.getParameter(), k -> new ArrayList<>()).add(condition);
        }
        return groups;
    }

    private boolean evaluateGroup(List<TurSNSiteSearchRuleCondition> group, TurSEParameters params, Locale locale) {
        if (group.isEmpty()) {
            return true;
        }
        boolean result = conditionMatches(group.getFirst(), params, locale);
        for (int i = 1; i < group.size(); i++) {
            TurSNSiteSearchRuleCondition condition = group.get(i);
            boolean condResult = conditionMatches(condition, params, locale);
            TurSNSiteSearchRuleLogicOperatorEnum logic = isOrOnlyParameter(condition.getParameter())
                    ? TurSNSiteSearchRuleLogicOperatorEnum.OR
                    : condition.getLogicOperator();
            if (logic == TurSNSiteSearchRuleLogicOperatorEnum.OR) {
                result = result || condResult;
            } else {
                result = result && condResult;
            }
        }
        return result;
    }

    private boolean conditionMatches(TurSNSiteSearchRuleCondition condition, TurSEParameters params, Locale locale) {
        if (condition.getParameter() == TurSNSiteSearchRuleParameterEnum.FILTER_QUERY) {
            return filterQueryConditionMatches(condition, params);
        }
        if (condition.getParameter() == TurSNSiteSearchRuleParameterEnum.LOCALE) {
            String localeStr = locale != null ? locale.toLanguageTag().replace("-", "_") : null;
            return condition.getValue() != null && condition.getValue().equals(localeStr);
        }
        if (condition.getParameter() == TurSNSiteSearchRuleParameterEnum.SORT) {
            String actualSort = params.getSort();
            return condition.getValue() != null && condition.getValue().equals(actualSort);
        }
        String actualValue = getParameterValue(condition.getParameter(), params);
        return operatorMatches(condition.getOperator(), actualValue, condition.getValue());
    }

    private boolean filterQueryConditionMatches(TurSNSiteSearchRuleCondition condition, TurSEParameters params) {
        List<String> fqValues = params.getTurSNFilterParams() != null
                ? params.getTurSNFilterParams().getDefaultValues()
                : Collections.emptyList();
        if (fqValues == null) {
            fqValues = Collections.emptyList();
        }

        String fieldName = condition.getFieldName();
        String expectedValue = condition.getValue();
        TurSNSiteSearchRuleOperatorEnum operator = condition.getOperator();

        if (operator == TurSNSiteSearchRuleOperatorEnum.IS_EMPTY) {
            if (StringUtils.hasText(fieldName)) {
                return fqValues.stream().noneMatch(fq -> fq.startsWith(fieldName + ":"));
            }
            return fqValues.isEmpty();
        }

        String safeExpectedValue = expectedValue != null ? expectedValue : "";
        String fqExpression = StringUtils.hasText(fieldName)
                ? fieldName + ":" + safeExpectedValue
                : expectedValue;

        return switch (operator) {
            case EQUALS -> fqValues.stream().anyMatch(fq -> fq.equals(fqExpression));
            case CONTAINS -> fqValues.stream().anyMatch(fq -> fq.contains(
                    fqExpression != null ? fqExpression : ""));
            case STARTS_WITH -> fqValues.stream().anyMatch(fq -> fq.startsWith(
                    fqExpression != null ? fqExpression : ""));
            case MATCHES_ANY -> fqValues.stream().anyMatch(fq -> fq.contains(
                    expectedValue != null ? expectedValue : ""));
            default -> false;
        };
    }

    private boolean operatorMatches(TurSNSiteSearchRuleOperatorEnum operator,
                                     String actualValue, String expectedValue) {
        return switch (operator) {
            case IS_EMPTY -> !StringUtils.hasText(actualValue);
            case EQUALS -> expectedValue != null && expectedValue.equals(actualValue);
            case CONTAINS -> actualValue != null && expectedValue != null && actualValue.contains(expectedValue);
            case STARTS_WITH -> actualValue != null && expectedValue != null && actualValue.startsWith(expectedValue);
            case MATCHES_ANY -> actualValue != null && expectedValue != null && actualValue.contains(expectedValue);
        };
    }

    private static boolean isOrOnlyParameter(TurSNSiteSearchRuleParameterEnum parameter) {
        return parameter == TurSNSiteSearchRuleParameterEnum.SORT
                || parameter == TurSNSiteSearchRuleParameterEnum.LOCALE;
    }

    private String getParameterValue(TurSNSiteSearchRuleParameterEnum parameter, TurSEParameters params) {
        return switch (parameter) {
            case QUERY -> params.getQuery();
            case SORT -> params.getSort();
            case LOCALE -> null;
            case FILTER_QUERY -> null;
        };
    }

    private void applyActions(TurSNSiteSearchRule rule, TurSEParameters params) {
        for (TurSNSiteSearchRuleAction action : rule.getActions()) {
            applyAction(action, params);
        }
    }

    @SuppressWarnings("java:S6916") // switch is over an enum; Java pattern-match guards
    // (`when`) apply only to type patterns, not enum constant labels, so the inner
    // null-check on ADD_FILTER_QUERY cannot be hoisted into a guard.
    private void applyAction(TurSNSiteSearchRuleAction action, TurSEParameters params) {
        TurSNSiteSearchRuleActionTypeEnum actionType = action.getActionType();
        String value = action.getValue();

        switch (actionType) {
            case SET_SORT -> params.setSort(value);
            case SET_ROWS -> {
                try {
                    params.setRows(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    log.warn("Invalid rows value in search rule action: {}", value);
                }
            }
            case ADD_FACETS -> {
                Set<String> toAdd = Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toSet());
                if (params.getAddedFacets() == null) {
                    params.setAddedFacets(new java.util.HashSet<>());
                }
                params.getAddedFacets().addAll(toAdd);
            }
            case REMOVE_FACETS -> {
                Set<String> toRemove = Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toSet());
                if (params.getRemovedFacets() == null) {
                    params.setRemovedFacets(new java.util.HashSet<>());
                }
                params.getRemovedFacets().addAll(toRemove);
            }
            case ADD_FILTER_QUERY -> {
                if (params.getTurSNFilterParams() != null) {
                    List<String> currentFq = new java.util.ArrayList<>(
                            params.getTurSNFilterParams().getDefaultValues() != null
                                    ? params.getTurSNFilterParams().getDefaultValues()
                                    : Collections.emptyList());
                    currentFq.add(value);
                    params.getTurSNFilterParams().setDefaultValues(currentFq);
                }
            }
            case SET_BOOST_QUERY -> params.setBoostQueries(List.of(value));
        }
    }
}
