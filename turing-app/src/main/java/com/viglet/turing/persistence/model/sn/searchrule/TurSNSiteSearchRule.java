package com.viglet.turing.persistence.model.sn.searchrule;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Search rule definition for a Semantic Navigation site.
 * Each rule has conditions (AND-ed) and actions to apply when all conditions match.
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
@Table(name = "sn_site_search_rule")
@JsonIgnoreProperties({"turSNSite"})
public class TurSNSiteSearchRule implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private int position;

    @Builder.Default
    @Column(nullable = false)
    private int enabled = 1;

    @Builder.Default
    @OneToMany(mappedBy = "turSNSiteSearchRule", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<TurSNSiteSearchRuleCondition> conditions = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "turSNSiteSearchRule", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<TurSNSiteSearchRuleAction> actions = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "sn_site_id", nullable = false)
    @JsonBackReference(value = "turSNSiteSearchRule-turSNSite")
    private TurSNSite turSNSite;

    public void setConditions(Set<TurSNSiteSearchRuleCondition> conditions) {
        this.conditions.clear();
        if (conditions != null) {
            for (TurSNSiteSearchRuleCondition condition : conditions) {
                condition.setTurSNSiteSearchRule(this);
                this.conditions.add(condition);
            }
        }
    }

    public void setActions(Set<TurSNSiteSearchRuleAction> actions) {
        this.actions.clear();
        if (actions != null) {
            for (TurSNSiteSearchRuleAction action : actions) {
                action.setTurSNSiteSearchRule(this);
                this.actions.add(action);
            }
        }
    }
}
