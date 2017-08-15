package com.viglet.turing.persistence.model.nlp.term;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;


/**
 * The persistent class for the turTermVariation database table.
 * 
 */
@Entity
@Table(name="turTermVariation")
@NamedQuery(name="TurTermVariation.findAll", query="SELECT tv FROM TurTermVariation tv")
@JsonIgnoreProperties({ "turTerm" } )
public class TurTermVariation implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=false, length=255)
	private String name;

	@Column(name="name_lower", nullable=false, length=255)
	private String nameLower;

	@Column(name="rule_accent", nullable=false)
	private int ruleAccent;

	@Column(name="rule_case", nullable=false)
	private int ruleCase;

	@Column(name="rule_prefix", length=255)
	private String rulePrefix;

	@Column(name="rule_prefix_required")
	private int rulePrefixRequired;

	@Column(name="rule_suffix", length=255)
	private String ruleSuffix;

	@Column(name="rule_suffix_required")
	private int ruleSuffixRequired;

	@Column(nullable=false)
	private double weight;

	//bi-directional many-to-one association to TurTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private TurTerm turTerm;

	//bi-directional many-to-one association to TurTermVariationLanguage
	@OneToMany(mappedBy="turTermVariation")
	private List<TurTermVariationLanguage> turTermVariationLanguages;

	public TurTermVariation() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getNameLower() {
		return this.nameLower;
	}

	public void setNameLower(String nameLower) {
		this.nameLower = nameLower;
	}

	public int getRuleAccent() {
		return this.ruleAccent;
	}

	public void setRuleAccent(int ruleAccent) {
		this.ruleAccent = ruleAccent;
	}

	public int getRuleCase() {
		return this.ruleCase;
	}

	public void setRuleCase(int ruleCase) {
		this.ruleCase = ruleCase;
	}

	public String getRulePrefix() {
		return this.rulePrefix;
	}

	public void setRulePrefix(String rulePrefix) {
		this.rulePrefix = rulePrefix;
	}

	public int getRulePrefixRequired() {
		return this.rulePrefixRequired;
	}

	public void setRulePrefixRequired(int rulePrefixRequired) {
		this.rulePrefixRequired = rulePrefixRequired;
	}

	public String getRuleSuffix() {
		return this.ruleSuffix;
	}

	public void setRuleSuffix(String ruleSuffix) {
		this.ruleSuffix = ruleSuffix;
	}

	public int getRuleSuffixRequired() {
		return this.ruleSuffixRequired;
	}

	public void setRuleSuffixRequired(int ruleSuffixRequired) {
		this.ruleSuffixRequired = ruleSuffixRequired;
	}

	public double getWeight() {
		return this.weight;
	}

	public void setWeight(double weight) {
		this.weight = weight;
	}

	public TurTerm getTurTerm() {
		return this.turTerm;
	}

	public void setTurTerm(TurTerm turTerm) {
		this.turTerm = turTerm;
	}

	public List<TurTermVariationLanguage> getTurTermVariationLanguages() {
		return this.turTermVariationLanguages;
	}

	public void setTurTermVariationLanguages(List<TurTermVariationLanguage> turTermVariationLanguages) {
		this.turTermVariationLanguages = turTermVariationLanguages;
	}

	public TurTermVariationLanguage addTurTermVariationLanguage(TurTermVariationLanguage turTermVariationLanguage) {
		getTurTermVariationLanguages().add(turTermVariationLanguage);
		turTermVariationLanguage.setTurTermVariation(this);

		return turTermVariationLanguage;
	}

	public TurTermVariationLanguage removeTurTermVariationLanguage(TurTermVariationLanguage turTermVariationLanguage) {
		getTurTermVariationLanguages().remove(turTermVariationLanguage);
		turTermVariationLanguage.setTurTermVariation(null);

		return turTermVariationLanguage;
	}

}