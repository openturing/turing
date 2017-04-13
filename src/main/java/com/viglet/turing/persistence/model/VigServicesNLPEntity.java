package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the vigServicesNLPEntities database table.
 * 
 */
@Entity
@Table(name="vigServicesNLPEntities")
@NamedQuery(name="VigServicesNLPEntity.findAll", query="SELECT v FROM VigServicesNLPEntity v")
public class VigServicesNLPEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=false)
	private int enabled;

	@Column(nullable=false, length=255)
	private String name;

	//bi-directional many-to-one association to VigEntity
	@ManyToOne
	@JoinColumn(name="id_entity", nullable=false)
	private VigEntity vigEntity;

	//bi-directional many-to-one association to VigService
	@ManyToOne
	@JoinColumn(name="id_service", nullable=false)
	private VigService vigService;

	public VigServicesNLPEntity() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getEnabled() {
		return this.enabled;
	}

	public void setEnabled(int enabled) {
		this.enabled = enabled;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public VigEntity getVigEntity() {
		return this.vigEntity;
	}

	public void setVigEntity(VigEntity vigEntity) {
		this.vigEntity = vigEntity;
	}

	public VigService getVigService() {
		return this.vigService;
	}

	public void setVigService(VigService vigService) {
		this.vigService = vigService;
	}

}