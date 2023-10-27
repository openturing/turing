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

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.persistence.model.ml.TurMLCategory;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;


/**
 * The persistent class for the turDataGroupCategory database table.
 * 
 */
@Getter
@Entity
@Table(name="turDataGroupCategory")
@NamedQuery(name="TurDataGroupCategory.findAll", query="SELECT dgc FROM TurDataGroupCategory dgc")
public class TurDataGroupCategory implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator = "id_Sequence")
    @SequenceGenerator(name = "id_Sequence", sequenceName = "ID_SEQ")
	@Column(unique=true, nullable=false)
	private int id;

	//bi-directional many-to-one association to TurCategory
	@ManyToOne
	@JoinColumn(name="ml_category_id", nullable=false)
	private TurMLCategory turMLCategory;

	//bi-directional many-to-one association to TurDataGroup
	@ManyToOne
	@JoinColumn(name="data_group_id", nullable=false)
	@JsonBackReference (value="turDataGroupCategory-turDataGroup")
	private TurDataGroup turDataGroup;

	public void setId(int id) {
		this.id = id;
	}

	public void setTurMLCategory(TurMLCategory turCategory) {
		this.turMLCategory = turCategory;
	}

	public void setTurDataGroup(TurDataGroup turDataGroup) {
		this.turDataGroup = turDataGroup;
	}

}