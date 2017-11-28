package com.viglet.turing.persistence.model.nlp;

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

	@Column(nullable=false, length=5)
	private String language;

	//bi-directional many-to-one association to VigEntity
	@ManyToOne
	@JoinColumn(name="entity_id", nullable=false)
	private TurNLPEntity turNLPEntity;

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

	public TurNLPEntity getTurNLPEntity() {
		return this.turNLPEntity;
	}

	public void setTurNLPEntity(TurNLPEntity turNLPEntity) {
		this.turNLPEntity = turNLPEntity;
	}

	public TurNLPVendor getTurNLPVendor() {
		return this.turNLPVendor;
	}

	public void setTurNLPVendor(TurNLPVendor turNLPVendor) {
		this.turNLPVendor = turNLPVendor;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}
	
}