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

package com.viglet.turing.persistence.model.storage;

import java.io.Serializable;
import javax.persistence.*;

import org.hibernate.annotations.Fetch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The persistent class for the turData database table.
 * 
 */
@Entity
@Table(name = "turData")
@NamedQuery(name = "TurData.findAll", query = "SELECT d FROM TurData d")
@JsonIgnoreProperties({ "turDataGroupData" })
public class TurData 

implements Serializable 

{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator = "id_Sequence")
    @SequenceGenerator(name = "id_Sequence", sequenceName = "ID_SEQ")
	@Column(unique = true, nullable = false)
	private int id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, length = 50)
	private String type;

	// bi-directional many-to-one association to TurDataGroupData
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turData", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurDataGroupData> turDataGroupData;

	// bi-directional many-to-one association to TurDataSentence
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turData", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurDataGroupSentence> turDataGroupSentences;

	public TurData() {
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

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public List<TurDataGroupData> getTurDataGroupData() {
		return this.turDataGroupData;
	}

	public void setTurDataGroupData(List<TurDataGroupData> turDataGroupData) {
		this.turDataGroupData = turDataGroupData;
	}

	public TurDataGroupData addTurDataGroupData(TurDataGroupData turDataGroupData) {
		getTurDataGroupData().add(turDataGroupData);
		turDataGroupData.setTurData(this);

		return turDataGroupData;
	}

	public TurDataGroupData removeTurDataGroupData(TurDataGroupData turDataGroupData) {
		getTurDataGroupData().remove(turDataGroupData);
		turDataGroupData.setTurData(null);

		return turDataGroupData;
	}

	public List<TurDataGroupSentence> getTurDataGroupSentences() {
		return this.turDataGroupSentences;
	}

	public void setTurDataGroupSentences(List<TurDataGroupSentence> turDataSentences) {
		this.turDataGroupSentences = turDataSentences;
	}

	public TurDataGroupSentence addTurDataSentence(TurDataGroupSentence turDataSentence) {
		getTurDataGroupSentences().add(turDataSentence);
		turDataSentence.setTurData(this);

		return turDataSentence;
	}

	public TurDataGroupSentence removeTurDataSentence(TurDataGroupSentence turDataSentence) {
		getTurDataGroupSentences().remove(turDataSentence);
		turDataSentence.setTurData(null);

		return turDataSentence;
	}

}