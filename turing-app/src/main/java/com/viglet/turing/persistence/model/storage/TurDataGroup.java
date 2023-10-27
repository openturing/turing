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

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Fetch;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * The persistent class for the turDataGroup database table.
 * 
 */
@Entity
@Table(name = "turDataGroup")
@NamedQuery(name = "TurDataGroup.findAll", query = "SELECT dg FROM TurDataGroup dg")
public class TurDataGroup implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Getter
	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator = "id_Sequence")
    @SequenceGenerator(name = "id_Sequence", sequenceName = "ID_SEQ")
	@Column(unique = true, nullable = false)
	private int id;

	@Getter
	@Column(length = 250)
	private String description;

	@Getter
	@Column(nullable = false, length = 50)
	private String name;

	// bi-directional many-to-one association to TurDataGroupCategory
	@Getter
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turDataGroup", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurDataGroupCategory> turDataGroupCategories;

	// bi-directional many-to-one association to TurDataGroupData
	@Getter
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turDataGroup", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurDataGroupData> turDataGroupData;

	// bi-directional many-to-one association to TurDataGroupData
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turDataGroup", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurDataGroupSentence> turDataGroupSentence;

	public void setId(int id) {
		this.id = id;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setTurDataGroupCategories(List<TurDataGroupCategory> turDataGroupCategories) {
		this.turDataGroupCategories = turDataGroupCategories;
	}

	public TurDataGroupCategory addTurDataGroupCategory(TurDataGroupCategory turDataGroupCategory) {
		getTurDataGroupCategories().add(turDataGroupCategory);
		turDataGroupCategory.setTurDataGroup(this);

		return turDataGroupCategory;
	}

	public TurDataGroupCategory removeTurDataGroupCategory(TurDataGroupCategory turDataGroupCategory) {
		getTurDataGroupCategories().remove(turDataGroupCategory);
		turDataGroupCategory.setTurDataGroup(null);

		return turDataGroupCategory;
	}

	public void setTurDataGroupData(List<TurDataGroupData> turDataGroupData) {
		this.turDataGroupData = turDataGroupData;
	}

	public TurDataGroupData addTurDataGroupData(TurDataGroupData turDataGroupData) {
		getTurDataGroupData().add(turDataGroupData);
		turDataGroupData.setTurDataGroup(this);

		return turDataGroupData;
	}

	public TurDataGroupData removeTurDataGroupData(TurDataGroupData turDataGroupData) {
		getTurDataGroupData().remove(turDataGroupData);
		turDataGroupData.setTurDataGroup(null);

		return turDataGroupData;
	}

}