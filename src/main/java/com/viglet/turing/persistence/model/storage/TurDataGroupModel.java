package com.viglet.turing.persistence.model.storage;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.viglet.turing.persistence.model.ml.TurMLCategory;
import com.viglet.turing.persistence.model.ml.TurMLModel;

/**
 * The persistent class for the turDataSentence database table.
 * 
 */
@Entity
@Table(name = "turDataGroupModel")
@NamedQuery(name = "TurDataGroupModel.findAll", query = "SELECT dm FROM TurDataGroupModel dm")
public class TurDataGroupModel implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(unique = true, nullable = false)
	private int id;

	// bi-directional many-to-one association to TurCategory
	@OneToOne
	private TurMLModel turMLModel;

	// bi-directional many-to-one association to TurData
	@ManyToOne
	@JoinColumn(name = "datagroup_id", nullable = false)
	@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
	@JsonIdentityReference(alwaysAsId = true)
	private TurDataGroup turDataGroup;

	public TurDataGroupModel() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}
	public TurMLModel getTurMLModel() {
		return turMLModel;
	}

	public void setTurMLModel(TurMLModel turMLModel) {
		this.turMLModel = turMLModel;
	}
	public TurDataGroup getTurDataGroup() {
		return turDataGroup;
	}

	public void setTurDataGroup(TurDataGroup turDataGroup) {
		this.turDataGroup = turDataGroup;
	}

}