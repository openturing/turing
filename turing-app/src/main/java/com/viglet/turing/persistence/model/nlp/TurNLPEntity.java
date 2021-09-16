/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.persistence.model.nlp;

import java.io.Serializable;
import javax.persistence.*;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.nlp.term.TurTerm;

import java.util.Set;

/**
 * The persistent class for the turEntities database table.
 * 
 */
@Entity
@Table(name = "turNLPEntity")
@NamedQuery(name = "TurNLPEntity.findAll", query = "SELECT ne FROM TurNLPEntity ne")
@JsonIgnoreProperties({ "turNLPInstanceEntities", "turNLPVendorEntities" } )
public class TurNLPEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

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
	
	@Column
	private int enabled;		

	// bi-directional many-to-one association to TurNLPInstanceEntity
	@OneToMany(mappedBy = "turNLPEntity", orphanRemoval = true)
	@Cascade({ CascadeType.ALL })
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurNLPInstanceEntity> turNLPInstanceEntities;
	

	// bi-directional many-to-one association to TurNLPVendorEntity
	@OneToMany(mappedBy = "turNLPEntity", orphanRemoval = true)
	@Cascade({ CascadeType.ALL })
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurNLPVendorEntity> turNLPVendorEntities;
	
	
	// bi-directional many-to-one association to TurTerm
	@OneToMany(mappedBy = "turNLPEntity", orphanRemoval = true)
	@Cascade({ CascadeType.ALL })
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurTerm> turTerms;

	public TurNLPEntity() {
	}

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
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

	public Set<TurNLPInstanceEntity> getTurNLPInstanceEntities() {
		return this.turNLPInstanceEntities;
	}

	public void setTurNLPInstanceEntities(Set<TurNLPInstanceEntity> turNLPInstanceEntities) {
		this.turNLPInstanceEntities = turNLPInstanceEntities;
	}

	public TurNLPInstanceEntity addTurNLPInstanceEntity(TurNLPInstanceEntity turNLPInstanceEntity) {
		getTurNLPInstanceEntities().add(turNLPInstanceEntity);
		turNLPInstanceEntity.setTurNLPEntity(this);

		return turNLPInstanceEntity;
	}

	public TurNLPInstanceEntity removeTurNLPInstanceEntity(TurNLPInstanceEntity turNLPInstanceEntity) {
		getTurNLPInstanceEntities().remove(turNLPInstanceEntity);
		turNLPInstanceEntity.setTurNLPEntity(null);

		return turNLPInstanceEntity;
	}

	
	
	public Set<TurNLPVendorEntity> getTurNLPVendorEntities() {
		return this.turNLPVendorEntities;
	}

	public void setTurNLPVendorEntities(Set<TurNLPVendorEntity> turNLPVendorEntities) {
		this.turNLPVendorEntities = turNLPVendorEntities;
	}

	public TurNLPVendorEntity addTurNLPVendorEntity(TurNLPVendorEntity turNLPVendorEntity) {
		getTurNLPVendorEntities().add(turNLPVendorEntity);
		turNLPVendorEntity.setTurNLPEntity(this);

		return turNLPVendorEntity;
	}

	public TurNLPVendorEntity removeTurNLPVendorEntity(TurNLPVendorEntity turNLPVendorEntity) {
		getTurNLPVendorEntities().remove(turNLPVendorEntity);
		turNLPVendorEntity.setTurNLPEntity(null);

		return turNLPVendorEntity;
	}
	public Set<TurTerm> getTurTerms() {
		return this.turTerms;
	}

	public void setTurTerms(Set<TurTerm> turTerms) {
		this.turTerms = turTerms;
	}

	public TurTerm addTurTerm(TurTerm turTerm) {
		getTurTerms().add(turTerm);
		turTerm.setTurNLPEntity(this);

		return turTerm;
	}

	public TurTerm removeTurTerm(TurTerm turTerm) {
		getTurTerms().remove(turTerm);
		turTerm.setTurNLPEntity(null);

		return turTerm;
	}

	public int getEnabled() {
		return enabled;
	}

	public void setEnabled(int enabled) {
		this.enabled = enabled;
	}

	

}