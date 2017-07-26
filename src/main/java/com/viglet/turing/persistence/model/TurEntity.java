package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the vigEntities database table.
 * 
 */
@Entity
@Table(name="turEntity")
@NamedQuery(name="TurEntity.findAll", query="SELECT e FROM TurEntity e")
public class TurEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(name="collection_name", nullable=false, length=50)
	private String collectionName;

	@Column(nullable=false, length=255)
	private String description;

	@Column(name="internal_name", nullable=false, length=50)
	private String internalName;

	@Column(nullable=false)
	private int local;

	@Column(nullable=false, length=50)
	private String name;

	//bi-directional many-to-one association to VigServicesNLPEntity
	@OneToMany(mappedBy="turEntity")
	private List<VigServicesNLPEntity> vigServicesNlpentities;

	//bi-directional many-to-one association to VigTerm
	@OneToMany(mappedBy="turEntity")
	private List<VigTerm> vigTerms;

	public TurEntity() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getInternalName() {
		return this.internalName;
	}

	public void setInternalName(String internalName) {
		this.internalName = internalName;
	}

	public int getLocal() {
		return this.local;
	}

	public void setLocal(int local) {
		this.local = local;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<VigServicesNLPEntity> getVigServicesNlpentities() {
		return this.vigServicesNlpentities;
	}

	public void setVigServicesNlpentities(List<VigServicesNLPEntity> vigServicesNlpentities) {
		this.vigServicesNlpentities = vigServicesNlpentities;
	}

	public VigServicesNLPEntity addVigServicesNlpentity(VigServicesNLPEntity vigServicesNlpentity) {
		getVigServicesNlpentities().add(vigServicesNlpentity);
		vigServicesNlpentity.setTurEntity(this);

		return vigServicesNlpentity;
	}

	public VigServicesNLPEntity removeVigServicesNlpentity(VigServicesNLPEntity vigServicesNlpentity) {
		getVigServicesNlpentities().remove(vigServicesNlpentity);
		vigServicesNlpentity.setTurEntity(null);

		return vigServicesNlpentity;
	}

	public List<VigTerm> getVigTerms() {
		return this.vigTerms;
	}

	public void setVigTerms(List<VigTerm> vigTerms) {
		this.vigTerms = vigTerms;
	}

	public VigTerm addVigTerm(VigTerm vigTerm) {
		getVigTerms().add(vigTerm);
		vigTerm.setTurEntity(this);

		return vigTerm;
	}

	public VigTerm removeVigTerm(VigTerm vigTerm) {
		getVigTerms().remove(vigTerm);
		vigTerm.setTurEntity(null);

		return vigTerm;
	}

}