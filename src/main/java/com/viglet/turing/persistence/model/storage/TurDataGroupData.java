package com.viglet.turing.persistence.model.storage;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonBackReference;


/**
 * The persistent class for the turDataGroupData database table.
 * 
 */
@Entity
@Table(name="turDataGroupData")
@NamedQuery(name="TurDataGroupData.findAll", query="SELECT dgd FROM TurDataGroupData dgd")
public class TurDataGroupData implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	//bi-directional many-to-one association to TurData
	@ManyToOne
	@JoinColumn(name="data_id", nullable=false)
	private TurData turData;

	//bi-directional many-to-one association to TurDataGroup
	@ManyToOne
	@JoinColumn(name="data_group_id", nullable=false)
	@JsonBackReference (value="turDataGroupData-turDataGroup")
	private TurDataGroup turDataGroup;

	public TurDataGroupData() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public TurData getTurData() {
		return this.turData;
	}

	public void setTurData(TurData turData) {
		this.turData = turData;
	}

	public TurDataGroup getTurDataGroup() {
		return this.turDataGroup;
	}

	public void setTurDataGroup(TurDataGroup turDataGroup) {
		this.turDataGroup = turDataGroup;
	}

}