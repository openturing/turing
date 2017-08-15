package com.viglet.turing.persistence.model.nlp;

import java.io.Serializable;
import javax.persistence.*;

import com.viglet.turing.persistence.model.nlp.term.TurTerm;

import java.util.List;

/**
 * The persistent class for the turEntities database table.
 * 
 */
@Entity
@Table(name = "turEntity")
@NamedQuery(name = "TurEntity.findAll", query = "SELECT e FROM TurEntity e")
public class TurEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(unique = true, nullable = false)
	private int id;

	@Column(name = "collection_name", nullable = false, length = 50)
	private String collectionName;

	@Column(nullable = false, length = 255)
	private String description;

	@Column(name = "internal_name", nullable = false, length = 50)
	private String internalName;

	@Column(nullable = false)
	private int local;

	@Column(nullable = false, length = 50)
	private String name;

	// bi-directional many-to-one association to TurServicesNLPEntity
	@OneToMany(mappedBy = "turEntity")
	private List<TurNLPInstanceEntity> turNLPInstanceEntities;

	// bi-directional many-to-one association to TurServicesNLPEntity
	@OneToMany(mappedBy = "turEntity")
	private List<TurNLPVendorEntity> turNLPVendorEntities;

	// bi-directional many-to-one association to TurTerm
	@OneToMany(mappedBy = "turEntity")
	private List<TurTerm> turTerms;

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

	public List<TurNLPInstanceEntity> getTurNLPInstanceEntities() {
		return this.turNLPInstanceEntities;
	}

	public void setTurNLPInstanceEntities(List<TurNLPInstanceEntity> turNLPInstanceEntities) {
		this.turNLPInstanceEntities = turNLPInstanceEntities;
	}

	public TurNLPInstanceEntity addTurNLPInstanceEntity(TurNLPInstanceEntity turNLPInstanceEntity) {
		getTurNLPInstanceEntities().add(turNLPInstanceEntity);
		turNLPInstanceEntity.setTurEntity(this);

		return turNLPInstanceEntity;
	}

	public TurNLPInstanceEntity removeTurNLPInstanceEntity(TurNLPInstanceEntity turNLPInstanceEntity) {
		getTurNLPInstanceEntities().remove(turNLPInstanceEntity);
		turNLPInstanceEntity.setTurEntity(null);

		return turNLPInstanceEntity;
	}

	
	
	public List<TurNLPVendorEntity> getTurNLPVendorEntities() {
		return this.turNLPVendorEntities;
	}

	public void setTurNLPVendorEntities(List<TurNLPVendorEntity> turNLPVendorEntities) {
		this.turNLPVendorEntities = turNLPVendorEntities;
	}

	public TurNLPVendorEntity addTurNLPVendorEntity(TurNLPVendorEntity turNLPVendorEntity) {
		getTurNLPVendorEntities().add(turNLPVendorEntity);
		turNLPVendorEntity.setTurEntity(this);

		return turNLPVendorEntity;
	}

	public TurNLPVendorEntity removeTurNLPVendorEntity(TurNLPVendorEntity turNLPVendorEntity) {
		getTurNLPVendorEntities().remove(turNLPVendorEntity);
		turNLPVendorEntity.setTurEntity(null);

		return turNLPVendorEntity;
	}
	public List<TurTerm> getTurTerms() {
		return this.turTerms;
	}

	public void setTurTerms(List<TurTerm> turTerms) {
		this.turTerms = turTerms;
	}

	public TurTerm addTurTerm(TurTerm turTerm) {
		getTurTerms().add(turTerm);
		turTerm.setTurEntity(this);

		return turTerm;
	}

	public TurTerm removeTurTerm(TurTerm turTerm) {
		getTurTerms().remove(turTerm);
		turTerm.setTurEntity(null);

		return turTerm;
	}

}