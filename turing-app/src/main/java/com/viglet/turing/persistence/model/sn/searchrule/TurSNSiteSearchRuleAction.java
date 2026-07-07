package com.viglet.turing.persistence.model.sn.searchrule;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An action to apply when all conditions of a search rule match.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Entity
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Table(name = "sn_site_search_rule_action")
@JsonIgnoreProperties({"turSNSiteSearchRule"})
public class TurSNSiteSearchRuleAction implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "actionType", nullable = false, length = 30)
    private TurSNSiteSearchRuleActionTypeEnum actionType;

    @Column(name = "value", nullable = false, length = 1000)
    private String value;

    @ManyToOne
    @JoinColumn(name = "search_rule_id", nullable = false)
    private TurSNSiteSearchRule turSNSiteSearchRule;
}
