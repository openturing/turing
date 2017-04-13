package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the vigDataGroup database table.
 * 
 */
@Entity
@Table(name="vigDataGroup")
@NamedQuery(name="VigDataGroup.findAll", query="SELECT v FROM VigDataGroup v")
public class VigDataGroup implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(length=250)
	private String description;

	@Column(nullable=false, length=50)
	private String name;

	//bi-directional many-to-one association to VigDataGroupCategory
	@OneToMany(mappedBy="vigDataGroup")
	private List<VigDataGroupCategory> vigDataGroupCategories;

	//bi-directional many-to-one association to VigDataGroupData
	@OneToMany(mappedBy="vigDataGroup")
	private List<VigDataGroupData> vigDataGroupData;

	public VigDataGroup() {
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
		vigDataGroupCategory.setVigDataGroup(this);

		return vigDataGroupCategory;
	}

	public VigDataGroupCategory removeVigDataGroupCategory(VigDataGroupCategory vigDataGroupCategory) {
		getVigDataGroupCategories().remove(vigDataGroupCategory);
		vigDataGroupCategory.setVigDataGroup(null);

		return vigDataGroupCategory;
	}

	public List<VigDataGroupData> getVigDataGroupData() {
		return this.vigDataGroupData;
	}

	public void setVigDataGroupData(List<VigDataGroupData> vigDataGroupData) {
		this.vigDataGroupData = vigDataGroupData;
	}

	public VigDataGroupData addVigDataGroupData(VigDataGroupData vigDataGroupData) {
		getVigDataGroupData().add(vigDataGroupData);
		vigDataGroupData.setVigDataGroup(this);

		return vigDataGroupData;
	}

	public VigDataGroupData removeVigDataGroupData(VigDataGroupData vigDataGroupData) {
		getVigDataGroupData().remove(vigDataGroupData);
		vigDataGroupData.setVigDataGroup(null);

		return vigDataGroupData;
	}

}