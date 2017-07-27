package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the vigServicesNLPEntities database table.
 * 
 */
@Entity
@Table(name="turNLPVendorEntity")
@NamedQuery(name="TurNLPVendorEntity.findAll", query="SELECT nve FROM TurNLPVendorEntity nve")
public class TurNLPVendorEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=false, length=255)
	private String name;

	//bi-directional many-to-one association to VigEntity
	@ManyToOne
	@JoinColumn(name="entity_id", nullable=false)
	private TurEntity turEntity;

	@ManyToOne
	@JoinColumn(name="nlp_vendor_id", nullable=false)
	private TurNLPVendor turNLPVendor;

	public TurNLPVendorEntity() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public TurEntity getTurEntity() {
		return this.turEntity;
	}

	public void setTurEntity(TurEntity turEntity) {
		this.turEntity = turEntity;
	}

	public TurNLPVendor getTurNLPVendor() {
		return this.turNLPVendor;
	}

	public void setTurNLPVendor(TurNLPVendor turNLPVendor) {
		this.turNLPVendor = turNLPVendor;
	}
}