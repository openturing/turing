package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the vigTermVariationLanguage database table.
 * 
 */
@Entity
@Table(name="vigTermVariationLanguage")
@NamedQuery(name="VigTermVariationLanguage.findAll", query="SELECT v FROM VigTermVariationLanguage v")
public class VigTermVariationLanguage implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=false, length=10)
	private String language;

	//bi-directional many-to-one association to VigTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private VigTerm vigTerm;

	//bi-directional many-to-one association to VigTermVariation
	@ManyToOne
	@JoinColumn(name="variation_id", nullable=false)
	private VigTermVariation vigTermVariation;

	public VigTermVariationLanguage() {
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

	public VigTerm getVigTerm() {
		return this.vigTerm;
	}

	public void setVigTerm(VigTerm vigTerm) {
		this.vigTerm = vigTerm;
	}

	public VigTermVariation getVigTermVariation() {
		return this.vigTermVariation;
	}

	public void setVigTermVariation(VigTermVariation vigTermVariation) {
		this.vigTermVariation = vigTermVariation;
	}

}