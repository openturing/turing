package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the vigTerm database table.
 * 
 */
@Entity
@Table(name="vigTerm")
@NamedQuery(name="VigTerm.findAll", query="SELECT v FROM VigTerm v")
public class VigTerm implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(name="id_custom", nullable=false, length=255)
	private String idCustom;

	@Column(nullable=false, length=255)
	private String name;

	//bi-directional many-to-one association to VigEntity
	@ManyToOne
	@JoinColumn(name="entity_id", nullable=false)
	private TurEntity turEntity;

	//bi-directional many-to-one association to VigTermAttribute
	@OneToMany(mappedBy="vigTerm")
	private List<VigTermAttribute> vigTermAttributes;

	//bi-directional many-to-one association to VigTermRelationFrom
	@OneToMany(mappedBy="vigTerm")
	private List<VigTermRelationFrom> vigTermRelationFroms;

	//bi-directional many-to-one association to VigTermRelationTo
	@OneToMany(mappedBy="vigTerm")
	private List<VigTermRelationTo> vigTermRelationTos;

	//bi-directional many-to-one association to VigTermVariation
	@OneToMany(mappedBy="vigTerm")
	private List<VigTermVariation> vigTermVariations;

	//bi-directional many-to-one association to VigTermVariationLanguage
	@OneToMany(mappedBy="vigTerm")
	private List<VigTermVariationLanguage> vigTermVariationLanguages;

	public VigTerm() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getIdCustom() {
		return this.idCustom;
	}

	public void setIdCustom(String idCustom) {
		this.idCustom = idCustom;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public TurEntity getVigEntity() {
		return this.turEntity;
	}

	public void setTurEntity(TurEntity vigEntity) {
		this.turEntity = vigEntity;
	}

	public List<VigTermAttribute> getVigTermAttributes() {
		return this.vigTermAttributes;
	}

	public void setVigTermAttributes(List<VigTermAttribute> vigTermAttributes) {
		this.vigTermAttributes = vigTermAttributes;
	}

	public VigTermAttribute addVigTermAttribute(VigTermAttribute vigTermAttribute) {
		getVigTermAttributes().add(vigTermAttribute);
		vigTermAttribute.setVigTerm(this);

		return vigTermAttribute;
	}

	public VigTermAttribute removeVigTermAttribute(VigTermAttribute vigTermAttribute) {
		getVigTermAttributes().remove(vigTermAttribute);
		vigTermAttribute.setVigTerm(null);

		return vigTermAttribute;
	}

	public List<VigTermRelationFrom> getVigTermRelationFroms() {
		return this.vigTermRelationFroms;
	}

	public void setVigTermRelationFroms(List<VigTermRelationFrom> vigTermRelationFroms) {
		this.vigTermRelationFroms = vigTermRelationFroms;
	}

	public VigTermRelationFrom addVigTermRelationFrom(VigTermRelationFrom vigTermRelationFrom) {
		getVigTermRelationFroms().add(vigTermRelationFrom);
		vigTermRelationFrom.setVigTerm(this);

		return vigTermRelationFrom;
	}

	public VigTermRelationFrom removeVigTermRelationFrom(VigTermRelationFrom vigTermRelationFrom) {
		getVigTermRelationFroms().remove(vigTermRelationFrom);
		vigTermRelationFrom.setVigTerm(null);

		return vigTermRelationFrom;
	}

	public List<VigTermRelationTo> getVigTermRelationTos() {
		return this.vigTermRelationTos;
	}

	public void setVigTermRelationTos(List<VigTermRelationTo> vigTermRelationTos) {
		this.vigTermRelationTos = vigTermRelationTos;
	}

	public VigTermRelationTo addVigTermRelationTo(VigTermRelationTo vigTermRelationTo) {
		getVigTermRelationTos().add(vigTermRelationTo);
		vigTermRelationTo.setVigTerm(this);

		return vigTermRelationTo;
	}

	public VigTermRelationTo removeVigTermRelationTo(VigTermRelationTo vigTermRelationTo) {
		getVigTermRelationTos().remove(vigTermRelationTo);
		vigTermRelationTo.setVigTerm(null);

		return vigTermRelationTo;
	}

	public List<VigTermVariation> getVigTermVariations() {
		return this.vigTermVariations;
	}

	public void setVigTermVariations(List<VigTermVariation> vigTermVariations) {
		this.vigTermVariations = vigTermVariations;
	}

	public VigTermVariation addVigTermVariation(VigTermVariation vigTermVariation) {
		getVigTermVariations().add(vigTermVariation);
		vigTermVariation.setVigTerm(this);

		return vigTermVariation;
	}

	public VigTermVariation removeVigTermVariation(VigTermVariation vigTermVariation) {
		getVigTermVariations().remove(vigTermVariation);
		vigTermVariation.setVigTerm(null);

		return vigTermVariation;
	}

	public List<VigTermVariationLanguage> getVigTermVariationLanguages() {
		return this.vigTermVariationLanguages;
	}

	public void setVigTermVariationLanguages(List<VigTermVariationLanguage> vigTermVariationLanguages) {
		this.vigTermVariationLanguages = vigTermVariationLanguages;
	}

	public VigTermVariationLanguage addVigTermVariationLanguage(VigTermVariationLanguage vigTermVariationLanguage) {
		getVigTermVariationLanguages().add(vigTermVariationLanguage);
		vigTermVariationLanguage.setVigTerm(this);

		return vigTermVariationLanguage;
	}

	public VigTermVariationLanguage removeVigTermVariationLanguage(VigTermVariationLanguage vigTermVariationLanguage) {
		getVigTermVariationLanguages().remove(vigTermVariationLanguage);
		vigTermVariationLanguage.setVigTerm(null);

		return vigTermVariationLanguage;
	}

}