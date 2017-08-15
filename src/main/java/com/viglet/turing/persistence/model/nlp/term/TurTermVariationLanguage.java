package com.viglet.turing.persistence.model.nlp.term;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * The persistent class for the turTermVariationLanguage database table.
 * 
 */
@Entity
@Table(name="turTermVariationLanguage")
@NamedQuery(name="TurTermVariationLanguage.findAll", query="SELECT tvl FROM TurTermVariationLanguage tvl")
@JsonIgnoreProperties({ "turTerm", "turTermVariation" } )
public class TurTermVariationLanguage implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=false, length=10)
	private String language;

	//bi-directional many-to-one association to TurTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private TurTerm turTerm;

	//bi-directional many-to-one association to TurTermVariation
	@ManyToOne
	@JoinColumn(name="variation_id", nullable=false)
	private TurTermVariation turTermVariation;

	public TurTermVariationLanguage() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getLanguage() {
		return this.language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public TurTerm getTurTerm() {
		return this.turTerm;
	}

	public void setTurTerm(TurTerm turTerm) {
		this.turTerm = turTerm;
	}

	public TurTermVariation getTurTermVariation() {
		return this.turTermVariation;
	}

	public void setTurTermVariation(TurTermVariation turTermVariation) {
		this.turTermVariation = turTermVariation;
	}

}