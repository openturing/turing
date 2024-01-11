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

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.viglet.turing.persistence.model.ml.TurMLCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * The persistent class for the turDataSentence database table.
 * 
 */
@Setter
@Entity
@Table(name = "data_group_sequence")
public class TurDataGroupSentence implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Getter
	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator = "id_Sequence")
    @SequenceGenerator(name = "id_Sequence", sequenceName = "ID_SEQ")
	@Column(unique = true, nullable = false)
	private int id;

	@Getter
	@Lob
	@Column(nullable = false, length = 100000 )
	private String sentence;

	// bi-directional many-to-one association to TurCategory
	@ManyToOne
	private TurMLCategory turMLCategory;

	// bi-directional many-to-one association to TurData
	@Getter
	@ManyToOne
	@JoinColumn(name = "datagroup_id", nullable = false)
	@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
	@JsonIdentityReference(alwaysAsId = true)
	private TurDataGroup turDataGroup;

	// bi-directional many-to-one association to TurData
	@Getter
	@ManyToOne
	@JoinColumn(name = "data_id")
	@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
	@JsonIdentityReference(alwaysAsId = true)
	private TurData turData;

	@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
	@JsonIdentityReference(alwaysAsId = true)
	public TurMLCategory getTurMLCategory() {
		return this.turMLCategory;
	}

}