package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the vigTermVariation database table.
 * 
 */
@Entity
@Table(name="vigTermVariation")
@NamedQuery(name="VigTermVariation.findAll", query="SELECT v FROM VigTermVariation v")
public class VigTermVariation implements Serializable {
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

	//bi-directional many-to-one association to VigTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private VigTerm vigTerm;

	//bi-directional many-to-one association to VigTermVariationLanguage
	@OneToMany(mappedBy="vigTermVariation")
	private List<VigTermVariationLanguage> vigTermVariationLanguages;

	public VigTermVariation() {
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

	public VigTerm getVigTerm() {
		return this.vigTerm;
	}

	public void setVigTerm(VigTerm vigTerm) {
		this.vigTerm = vigTerm;
	}

	public List<VigTermVariationLanguage> getVigTermVariationLanguages() {
		return this.vigTermVariationLanguages;
	}

	public void setVigTermVariationLanguages(List<VigTermVariationLanguage> vigTermVariationLanguages) {
		this.vigTermVariationLanguages = vigTermVariationLanguages;
	}

	public VigTermVariationLanguage addVigTermVariationLanguage(VigTermVariationLanguage vigTermVariationLanguage) {
		getVigTermVariationLanguages().add(vigTermVariationLanguage);
		vigTermVariationLanguage.setVigTermVariation(this);

		return vigTermVariationLanguage;
	}

	public VigTermVariationLanguage removeVigTermVariationLanguage(VigTermVariationLanguage vigTermVariationLanguage) {
		getVigTermVariationLanguages().remove(vigTermVariationLanguage);
		vigTermVariationLanguage.setVigTermVariation(null);

		return vigTermVariationLanguage;
	}

}