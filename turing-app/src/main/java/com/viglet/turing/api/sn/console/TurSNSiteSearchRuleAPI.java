package com.viglet.turing.api.sn.console;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRule;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleAction;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleActionTypeEnum;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleCondition;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleLogicOperatorEnum;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleOperatorEnum;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleParameterEnum;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.searchrule.TurSNSiteSearchRuleRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * REST API for managing search rules on a Semantic Navigation site.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@RestController
@RequestMapping("/api/sn/{snSiteId}/search-rule")
@Tag(name = "Semantic Navigation Search Rule", description = "Semantic Navigation Search Rule API")
@Transactional
public class TurSNSiteSearchRuleAPI {
    private static final String SEARCH_RULE_NOT_FOUND = "Search rule not found.";

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteSearchRuleRepository turSNSiteSearchRuleRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    public TurSNSiteSearchRuleAPI(TurSNSiteRepository turSNSiteRepository,
                                   TurSNSiteSearchRuleRepository turSNSiteSearchRuleRepository,
                                   TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteSearchRuleRepository = turSNSiteSearchRuleRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    @Operation(summary = "Semantic Navigation Site Search Rule List")
    @GetMapping
    public List<TurSNSiteSearchRuleDto> list(@PathVariable String snSiteId) {
        TurSNSite turSNSite = getSite(snSiteId);
        return turSNSiteSearchRuleRepository.findByTurSNSiteWithDetails(turSNSite).stream()
                .map(this::toDto)
                .toList();
    }

    @Operation(summary = "Show a Semantic Navigation Site Search Rule")
    @GetMapping("/{searchRuleId}")
    public TurSNSiteSearchRuleDto get(@PathVariable String snSiteId,
                                       @PathVariable String searchRuleId) {
        TurSNSite turSNSite = getSite(snSiteId);
        return turSNSiteSearchRuleRepository.findByTurSNSiteAndId(turSNSite, searchRuleId)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, SEARCH_RULE_NOT_FOUND));
    }

    @Operation(summary = "Get available fields for search rule conditions")
    @GetMapping("/fields")
    public List<TurSNSiteSearchRuleFieldOptionDto> getFieldOptions(@PathVariable String snSiteId) {
        TurSNSite turSNSite = getSite(snSiteId);
        return turSNSiteFieldExtRepository.findByTurSNSite(Sort.by(Sort.Order.asc("name")), turSNSite).stream()
                .map(field -> {
                    TurSNSiteSearchRuleFieldOptionDto dto = new TurSNSiteSearchRuleFieldOptionDto();
                    dto.setId(field.getId());
                    dto.setName(field.getName());
                    dto.setType(field.getType() != null ? field.getType().toString() : null);
                    dto.setFacet(field.getFacet() == 1);
                    dto.setFacetName(field.getFacetName());
                    return dto;
                })
                .toList();
    }

    @Operation(summary = "Create a Semantic Navigation Site Search Rule")
    @PostMapping
    public TurSNSiteSearchRuleDto create(@PathVariable String snSiteId,
                                          @RequestBody TurSNSiteSearchRuleDto payload) {
        TurSNSite turSNSite = getSite(snSiteId);
        validatePayload(payload);

        TurSNSiteSearchRule searchRule = TurSNSiteSearchRule.builder().build();
        searchRule.setTurSNSite(turSNSite);
        applyPayload(searchRule, payload);

        TurSNSiteSearchRule saved = turSNSiteSearchRuleRepository.save(searchRule);
        return toDto(saved);
    }

    @Operation(summary = "Update a Semantic Navigation Site Search Rule")
    @PutMapping("/{searchRuleId}")
    public TurSNSiteSearchRuleDto update(@PathVariable String snSiteId,
                                          @PathVariable String searchRuleId,
                                          @RequestBody TurSNSiteSearchRuleDto payload) {
        TurSNSite turSNSite = getSite(snSiteId);
        validatePayload(payload);

        TurSNSiteSearchRule searchRule = turSNSiteSearchRuleRepository.findByTurSNSiteAndId(turSNSite, searchRuleId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, SEARCH_RULE_NOT_FOUND));

        applyPayload(searchRule, payload);
        TurSNSiteSearchRule saved = turSNSiteSearchRuleRepository.save(searchRule);
        return toDto(saved);
    }

    @Operation(summary = "Delete a Semantic Navigation Site Search Rule")
    @DeleteMapping("/{searchRuleId}")
    public boolean delete(@PathVariable String snSiteId,
                          @PathVariable String searchRuleId) {
        TurSNSite turSNSite = getSite(snSiteId);
        TurSNSiteSearchRule searchRule = turSNSiteSearchRuleRepository.findByTurSNSiteAndId(turSNSite, searchRuleId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, SEARCH_RULE_NOT_FOUND));
        turSNSiteSearchRuleRepository.delete(searchRule);
        return true;
    }

    private void validatePayload(TurSNSiteSearchRuleDto payload) {
        if (payload == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid search rule payload.");
        }
        if (payload.getName() == null || payload.getName().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Search rule name is required.");
        }
    }

    private void applyPayload(TurSNSiteSearchRule target, TurSNSiteSearchRuleDto payload) {
        target.setName(payload.getName());
        target.setDescription(payload.getDescription());
        target.setPosition(Optional.ofNullable(payload.getPosition()).orElse(0));
        target.setEnabled(Boolean.TRUE.equals(payload.getEnabled()) ? 1 : 0);

        Set<TurSNSiteSearchRuleCondition> conditions = Optional.ofNullable(payload.getConditions())
                .orElse(List.of())
                .stream()
                .map(this::toConditionEntity)
                .collect(Collectors.toCollection(HashSet::new));
        target.setConditions(conditions);

        Set<TurSNSiteSearchRuleAction> actions = Optional.ofNullable(payload.getActions())
                .orElse(List.of())
                .stream()
                .map(this::toActionEntity)
                .collect(Collectors.toCollection(HashSet::new));
        target.setActions(actions);
    }

    private TurSNSiteSearchRuleCondition toConditionEntity(TurSNSiteSearchRuleConditionDto dto) {
        TurSNSiteSearchRuleCondition condition = TurSNSiteSearchRuleCondition.builder().build();
        condition.setId(dto.getId());
        condition.setParameter(dto.getParameter());
        condition.setOperator(dto.getOperator());
        condition.setFieldName(dto.getFieldName());
        condition.setValue(dto.getValue());
        condition.setLogicOperator(Optional.ofNullable(dto.getLogicOperator())
                .orElse(TurSNSiteSearchRuleLogicOperatorEnum.AND));
        return condition;
    }

    private TurSNSiteSearchRuleAction toActionEntity(TurSNSiteSearchRuleActionDto dto) {
        TurSNSiteSearchRuleAction action = TurSNSiteSearchRuleAction.builder().build();
        action.setId(dto.getId());
        action.setActionType(dto.getActionType());
        action.setValue(dto.getValue());
        return action;
    }

    private TurSNSiteSearchRuleDto toDto(TurSNSiteSearchRule searchRule) {
        List<TurSNSiteSearchRuleConditionDto> conditions = Optional.ofNullable(searchRule.getConditions())
                .orElse(Set.of())
                .stream()
                .map(condition -> {
                    TurSNSiteSearchRuleConditionDto dto = new TurSNSiteSearchRuleConditionDto();
                    dto.setId(condition.getId());
                    dto.setParameter(condition.getParameter());
                    dto.setOperator(condition.getOperator());
                    dto.setFieldName(condition.getFieldName());
                    dto.setValue(condition.getValue());
                    dto.setLogicOperator(condition.getLogicOperator());
                    return dto;
                })
                .toList();

        List<TurSNSiteSearchRuleActionDto> actions = Optional.ofNullable(searchRule.getActions())
                .orElse(Set.of())
                .stream()
                .map(action -> {
                    TurSNSiteSearchRuleActionDto dto = new TurSNSiteSearchRuleActionDto();
                    dto.setId(action.getId());
                    dto.setActionType(action.getActionType());
                    dto.setValue(action.getValue());
                    return dto;
                })
                .toList();

        TurSNSiteSearchRuleDto dto = new TurSNSiteSearchRuleDto();
        dto.setId(searchRule.getId());
        dto.setName(searchRule.getName());
        dto.setDescription(searchRule.getDescription());
        dto.setPosition(searchRule.getPosition());
        dto.setEnabled(searchRule.getEnabled() == 1);
        dto.setConditions(conditions);
        dto.setActions(actions);
        return dto;
    }

    private TurSNSite getSite(String snSiteId) {
        return turSNSiteRepository.findById(snSiteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "SN Site not found."));
    }

    @Setter
    @Getter
    @NoArgsConstructor
    public static class TurSNSiteSearchRuleConditionDto {
        private String id;
        private TurSNSiteSearchRuleParameterEnum parameter;
        private TurSNSiteSearchRuleOperatorEnum operator;
        private String fieldName;
        private String value;
        private TurSNSiteSearchRuleLogicOperatorEnum logicOperator;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    public static class TurSNSiteSearchRuleActionDto {
        private String id;
        private TurSNSiteSearchRuleActionTypeEnum actionType;
        private String value;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    public static class TurSNSiteSearchRuleDto {
        private String id;
        private String name;
        private String description;
        private Integer position;
        private Boolean enabled;
        private List<TurSNSiteSearchRuleConditionDto> conditions;
        private List<TurSNSiteSearchRuleActionDto> actions;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    public static class TurSNSiteSearchRuleFieldOptionDto {
        private String id;
        private String name;
        private String type;
        private boolean facet;
        private String facetName;
    }
}
