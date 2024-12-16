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

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;

import java.util.List;


/**
 * The persistent class for the turTerm database table.
 * 
 */
@Entity
@Table(name="turTerm")
@NamedQuery(name="TurTerm.findAll", query="SELECT t FROM TurTerm t")
@JsonIgnoreProperties({ "turNLPEntity" } )
public class TurTerm implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(name="id_custom", nullable=false, length=255)
	private String idCustom;

	@Column(nullable=false, length=255)
	private String name;

	//bi-directional many-to-one association to TurNLPEntity
	@ManyToOne
	@JoinColumn(name="entity_id", nullable=false)
	private TurNLPEntity turNLPEntity;

	//bi-directional many-to-one association to TurTermAttribute
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turTerm", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurTermAttribute> turTermAttributes;

	//bi-directional many-to-one association to TurTermRelationFrom
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turTerm", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurTermRelationFrom> turTermRelationFroms;

	//bi-directional many-to-one association to TurTermRelationTo
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turTerm", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurTermRelationTo> turTermRelationTos;

	//bi-directional many-to-one association to TurTermVariation
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turTerm", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurTermVariation> turTermVariations;

	//bi-directional many-to-one association to TurTermVariationLanguage
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turTerm", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurTermVariationLanguage> turTermVariationLanguages;

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getIdCustom() {
		return this.idCustom;
	}

	public void setIdCustom(String idCustom) {
		this.idCustom = idCustom;
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

	public List<TurTermAttribute> getTurTermAttributes() {
		return this.turTermAttributes;
	}

	public void setTurTermAttributes(List<TurTermAttribute> turTermAttributes) {
		this.turTermAttributes = turTermAttributes;
	}

	public TurTermAttribute addTurTermAttribute(TurTermAttribute turTermAttribute) {
		getTurTermAttributes().add(turTermAttribute);
		turTermAttribute.setTurTerm(this);

		return turTermAttribute;
	}

	public TurTermAttribute removeTurTermAttribute(TurTermAttribute turTermAttribute) {
		getTurTermAttributes().remove(turTermAttribute);
		turTermAttribute.setTurTerm(null);

		return turTermAttribute;
	}

	public List<TurTermRelationFrom> getTurTermRelationFroms() {
		return this.turTermRelationFroms;
	}

	public void setTurTermRelationFroms(List<TurTermRelationFrom> turTermRelationFroms) {
		this.turTermRelationFroms = turTermRelationFroms;
	}

	public TurTermRelationFrom addTurTermRelationFrom(TurTermRelationFrom turTermRelationFrom) {
		getTurTermRelationFroms().add(turTermRelationFrom);
		turTermRelationFrom.setTurTerm(this);

		return turTermRelationFrom;
	}

	public TurTermRelationFrom removeTurTermRelationFrom(TurTermRelationFrom turTermRelationFrom) {
		getTurTermRelationFroms().remove(turTermRelationFrom);
		turTermRelationFrom.setTurTerm(null);

		return turTermRelationFrom;
	}

	public List<TurTermRelationTo> getTurTermRelationTos() {
		return this.turTermRelationTos;
	}

	public void setTurTermRelationTos(List<TurTermRelationTo> turTermRelationTos) {
		this.turTermRelationTos = turTermRelationTos;
	}

	public TurTermRelationTo addTurTermRelationTo(TurTermRelationTo turTermRelationTo) {
		getTurTermRelationTos().add(turTermRelationTo);
		turTermRelationTo.setTurTerm(this);

		return turTermRelationTo;
	}

	public TurTermRelationTo removeTurTermRelationTo(TurTermRelationTo turTermRelationTo) {
		getTurTermRelationTos().remove(turTermRelationTo);
		turTermRelationTo.setTurTerm(null);

		return turTermRelationTo;
	}

	public List<TurTermVariation> getTurTermVariations() {
		return this.turTermVariations;
	}

	public void setTurTermVariations(List<TurTermVariation> turTermVariations) {
		this.turTermVariations = turTermVariations;
	}

	public TurTermVariation addTurTermVariation(TurTermVariation turTermVariation) {
		getTurTermVariations().add(turTermVariation);
		turTermVariation.setTurTerm(this);

		return turTermVariation;
	}

	public TurTermVariation removeTurTermVariation(TurTermVariation turTermVariation) {
		getTurTermVariations().remove(turTermVariation);
		turTermVariation.setTurTerm(null);

		return turTermVariation;
	}

	public List<TurTermVariationLanguage> getTurTermVariationLanguages() {
		return this.turTermVariationLanguages;
	}

	public void setTurTermVariationLanguages(List<TurTermVariationLanguage> turTermVariationLanguages) {
		this.turTermVariationLanguages = turTermVariationLanguages;
	}

	public TurTermVariationLanguage addTurTermVariationLanguage(TurTermVariationLanguage turTermVariationLanguage) {
		getTurTermVariationLanguages().add(turTermVariationLanguage);
		turTermVariationLanguage.setTurTerm(this);

		return turTermVariationLanguage;
	}

	public TurTermVariationLanguage removeTurTermVariationLanguage(TurTermVariationLanguage turTermVariationLanguage) {
		getTurTermVariationLanguages().remove(turTermVariationLanguage);
		turTermVariationLanguage.setTurTerm(null);

		return turTermVariationLanguage;
	}

}