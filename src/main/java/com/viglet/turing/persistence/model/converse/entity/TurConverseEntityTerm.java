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

package com.viglet.turing.persistence.model.converse.entity;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

/**
 * The persistent class for the turConverseEntityTerm database table.
 * 
 */
@Entity
@Table(name = "turConverseEntityTerm")
@NamedQuery(name = "TurConverseEntityTerm.findAll", query = "SELECT cet FROM TurConverseEntityTerm cet")
@JsonIgnoreProperties({ "entity" })
public class TurConverseEntityTerm implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 50)
	private String name;

	@ElementCollection
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@CollectionTable(name = "tur_converse_entity_synonyms")
	@JoinColumn(name = "term_id")
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<String> synonyms = new HashSet<>();

	@ManyToOne
	@JoinColumn(name = "entity_id")
	private TurConverseEntity entity;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Set<String> getSynonyms() {
		return synonyms;
	}

	public void setSynonyms(Set<String> synonyms) {
		this.synonyms = synonyms;
	}

	public TurConverseEntity getEntity() {
		return entity;
	}

	public void setEntity(TurConverseEntity entity) {
		this.entity = entity;
	}

}