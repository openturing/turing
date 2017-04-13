package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the vigDataGroupCategory database table.
 * 
 */
@Entity
@Table(name="vigDataGroupCategory")
@NamedQuery(name="VigDataGroupCategory.findAll", query="SELECT v FROM VigDataGroupCategory v")
public class VigDataGroupCategory implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	//bi-directional many-to-one association to VigCategory
	@ManyToOne
	@JoinColumn(name="category_id", nullable=false)
	private VigCategory vigCategory;

	//bi-directional many-to-one association to VigDataGroup
	@ManyToOne
	@JoinColumn(name="data_group_id", nullable=false)
	private VigDataGroup vigDataGroup;

	public VigDataGroupCategory() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public VigCategory getVigCategory() {
		return this.vigCategory;
	}

	public void setVigCategory(VigCategory vigCategory) {
		this.vigCategory = vigCategory;
	}

	public VigDataGroup getVigDataGroup() {
		return this.vigDataGroup;
	}

	public void setVigDataGroup(VigDataGroup vigDataGroup) {
		this.vigDataGroup = vigDataGroup;
	}

}