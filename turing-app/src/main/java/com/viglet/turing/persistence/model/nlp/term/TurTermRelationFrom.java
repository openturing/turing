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

package com.viglet.turing.persistence.model.nlp.term;

import java.io.Serializable;
import javax.persistence.*;

import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;


/**
 * The persistent class for the turTermRelationFrom database table.
 * 
 */
@Entity
@Table(name="turTermRelationFrom")
@NamedQuery(name="TurTermRelationFrom.findAll", query="SELECT trf FROM TurTermRelationFrom trf")
@JsonIgnoreProperties({ "turTerm" } )
public class TurTermRelationFrom implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(name="relation_type", nullable=false)
	private int relationType;

	//bi-directional many-to-one association to TurTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private TurTerm turTerm;

	//bi-directional many-to-one association to TurTermRelationTo
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turTermRelationFrom", cascade = CascadeType.ALL)
	private List<TurTermRelationTo> turTermRelationTos;

	public TurTermRelationFrom() {
	}

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getRelationType() {
		return this.relationType;
	}

	public void setRelationType(int relationType) {
		this.relationType = relationType;
	}

	public TurTerm getTurTerm() {
		return this.turTerm;
	}

	public void setTurTerm(TurTerm turTerm) {
		this.turTerm = turTerm;
	}

	public List<TurTermRelationTo> getTurTermRelationTos() {
		return this.turTermRelationTos;
	}

	public void setTurTermRelationTos(List<TurTermRelationTo> turTermRelationTos) {
		this.turTermRelationTos = turTermRelationTos;
	}

	public TurTermRelationTo addTurTermRelationTo(TurTermRelationTo turTermRelationTo) {
		getTurTermRelationTos().add(turTermRelationTo);
		turTermRelationTo.setTurTermRelationFrom(this);

		return turTermRelationTo;
	}

	public TurTermRelationTo removeTurTermRelationTo(TurTermRelationTo turTermRelationTo) {
		getTurTermRelationTos().remove(turTermRelationTo);
		turTermRelationTo.setTurTermRelationFrom(null);

		return turTermRelationTo;
	}

}