/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.persistence.model.storage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * The persistent class for the turData database table.
 * 
 */
@Entity
@Table(name = "turData")
@NamedQuery(name = "TurData.findAll", query = "SELECT d FROM TurData d")
@JsonIgnoreProperties({ "turDataGroupData" })
public class TurData implements Serializable
{
	@Serial
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