package com.viglet.turing.persistence.model.storage;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.persistence.model.ml.TurMLCategory;


/**
 * The persistent class for the turDataGroupCategory database table.
 * 
 */
@Entity
@Table(name="turDataGroupCategory")
@NamedQuery(name="TurDataGroupCategory.findAll", query="SELECT dgc FROM TurDataGroupCategory dgc")
public class TurDataGroupCategory implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	//bi-directional many-to-one association to TurCategory
	@ManyToOne
	@JoinColumn(name="ml_category_id", nullable=false)
	@JsonBackReference (value="turDataGroupCategory-turMLCategory")
	private TurMLCategory turMLCategory;

	//bi-directional many-to-one association to TurDataGroup
	@ManyToOne
	@JoinColumn(name="data_group_id", nullable=false)
	@JsonBackReference (value="turDataGroupCategory-turDataGroup")
	private TurDataGroup turDataGroup;

	public TurDataGroupCategory() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public TurMLCategory getTurMLCategory() {
		return this.turMLCategory;
	}

	public void setTurMLCategory(TurMLCategory turCategory) {
		this.turMLCategory = turCategory;
	}

	public TurDataGroup getTurDataGroup() {
		return this.turDataGroup;
	}

	public void setTurDataGroup(TurDataGroup turDataGroup) {
		this.turDataGroup = turDataGroup;
	}

}