package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the vigDataGroupData database table.
 * 
 */
@Entity
@Table(name="vigDataGroupData")
@NamedQuery(name="VigDataGroupData.findAll", query="SELECT v FROM VigDataGroupData v")
public class VigDataGroupData implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	//bi-directional many-to-one association to VigData
	@ManyToOne
	@JoinColumn(name="data_id", nullable=false)
	private VigData vigData;

	//bi-directional many-to-one association to VigDataGroup
	@ManyToOne
	@JoinColumn(name="data_group_id", nullable=false)
	private VigDataGroup vigDataGroup;

	public VigDataGroupData() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public VigData getVigData() {
		return this.vigData;
	}

	public void setVigData(VigData vigData) {
		this.vigData = vigData;
	}

	public VigDataGroup getVigDataGroup() {
		return this.vigDataGroup;
	}

	public void setVigDataGroup(VigDataGroup vigDataGroup) {
		this.vigDataGroup = vigDataGroup;
	}

}