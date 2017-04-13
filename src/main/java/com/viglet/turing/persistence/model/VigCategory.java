package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the vigCategory database table.
 * 
 */
@Entity
@Table(name="vigCategory")
@NamedQuery(name="VigCategory.findAll", query="SELECT v FROM VigCategory v")
public class VigCategory implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(length=255)
	private String description;

	@Column(name="internal_name", nullable=false, length=50)
	private String internalName;

	@Column(nullable=false, length=50)
	private String name;

	//bi-directional many-to-one association to VigDataGroupCategory
	@OneToMany(mappedBy="vigCategory")
	private List<VigDataGroupCategory> vigDataGroupCategories;

	//bi-directional many-to-one association to VigDataSentence
	@OneToMany(mappedBy="vigCategory")
	private List<VigDataSentence> vigDataSentences;

	public VigCategory() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getInternalName() {
		return this.internalName;
	}

	public void setInternalName(String internalName) {
		this.internalName = internalName;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<VigDataGroupCategory> getVigDataGroupCategories() {
		return this.vigDataGroupCategories;
	}

	public void setVigDataGroupCategories(List<VigDataGroupCategory> vigDataGroupCategories) {
		this.vigDataGroupCategories = vigDataGroupCategories;
	}

	public VigDataGroupCategory addVigDataGroupCategory(VigDataGroupCategory vigDataGroupCategory) {
		getVigDataGroupCategories().add(vigDataGroupCategory);
		vigDataGroupCategory.setVigCategory(this);

		return vigDataGroupCategory;
	}

	public VigDataGroupCategory removeVigDataGroupCategory(VigDataGroupCategory vigDataGroupCategory) {
		getVigDataGroupCategories().remove(vigDataGroupCategory);
		vigDataGroupCategory.setVigCategory(null);

		return vigDataGroupCategory;
	}

	public List<VigDataSentence> getVigDataSentences() {
		return this.vigDataSentences;
	}

	public void setVigDataSentences(List<VigDataSentence> vigDataSentences) {
		this.vigDataSentences = vigDataSentences;
	}

	public VigDataSentence addVigDataSentence(VigDataSentence vigDataSentence) {
		getVigDataSentences().add(vigDataSentence);
		vigDataSentence.setVigCategory(this);

		return vigDataSentence;
	}

	public VigDataSentence removeVigDataSentence(VigDataSentence vigDataSentence) {
		getVigDataSentences().remove(vigDataSentence);
		vigDataSentence.setVigCategory(null);

		return vigDataSentence;
	}

}