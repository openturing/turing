package com.viglet.turing.persistence.model.storage;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the turDataGroup database table.
 * 
 */
@Entity
@Table(name="turDataGroup")
@NamedQuery(name="TurDataGroup.findAll", query="SELECT dg FROM TurDataGroup dg")
public class TurDataGroup implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(length=250)
	private String description;

	@Column(nullable=false, length=50)
	private String name;

	//bi-directional many-to-one association to TurDataGroupCategory
	@OneToMany(mappedBy="turDataGroup")
	private List<TurDataGroupCategory> turDataGroupCategories;

	//bi-directional many-to-one association to TurDataGroupData
	@OneToMany(mappedBy="turDataGroup")
	private List<TurDataGroupData> turDataGroupData;

	public TurDataGroup() {
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

	public List<TurDataGroupCategory> getTurDataGroupCategories() {
		return this.turDataGroupCategories;
	}

	public void setTurDataGroupCategories(List<TurDataGroupCategory> turDataGroupCategories) {
		this.turDataGroupCategories = turDataGroupCategories;
	}

	public TurDataGroupCategory addTurDataGroupCategory(TurDataGroupCategory turDataGroupCategory) {
		getTurDataGroupCategories().add(turDataGroupCategory);
		turDataGroupCategory.setTurDataGroup(this);

		return turDataGroupCategory;
	}

	public TurDataGroupCategory removeTurDataGroupCategory(TurDataGroupCategory turDataGroupCategory) {
		getTurDataGroupCategories().remove(turDataGroupCategory);
		turDataGroupCategory.setTurDataGroup(null);

		return turDataGroupCategory;
	}

	public List<TurDataGroupData> getTurDataGroupData() {
		return this.turDataGroupData;
	}

	public void setTurDataGroupData(List<TurDataGroupData> turDataGroupData) {
		this.turDataGroupData = turDataGroupData;
	}

	public TurDataGroupData addTurDataGroupData(TurDataGroupData turDataGroupData) {
		getTurDataGroupData().add(turDataGroupData);
		turDataGroupData.setTurDataGroup(this);

		return turDataGroupData;
	}

	public TurDataGroupData removeTurDataGroupData(TurDataGroupData turDataGroupData) {
		getTurDataGroupData().remove(turDataGroupData);
		turDataGroupData.setTurDataGroup(null);

		return turDataGroupData;
	}

}