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

package com.viglet.turing.persistence.model.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.storage.TurDataGroupCategory;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;


/**
 * The persistent class for the turCategory database table.
 * 
 */
@Getter
@Setter
@Entity
@Table(name="tur_ml_category")
@JsonIgnoreProperties({ "turDataGroupCategories" })
public class TurMLCategory implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator = "id_Sequence")
    @SequenceGenerator(name = "id_Sequence", sequenceName = "ID_SEQ")
	@Column(unique=true, nullable=false)
	private int id;

	@Column
	private String description;

	@Column(name="internal_name", nullable=false, length=50)
	private String internalName;

	@Column(nullable=false, length=50)
	private String name;

	//bi-directional many-to-one association to TurDataGroupCategory
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turMLCategory", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurDataGroupCategory> turDataGroupCategories;

	//bi-directional many-to-one association to TurDataSentence
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turMLCategory", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurDataGroupSentence> turDataGroupSentences;


	public TurDataGroupCategory addTurDataGroupCategory(TurDataGroupCategory turDataGroupCategory) {
		getTurDataGroupCategories().add(turDataGroupCategory);
		turDataGroupCategory.setTurMLCategory(this);

		return turDataGroupCategory;
	}

	public TurDataGroupCategory removeTurDataGroupCategory(TurDataGroupCategory turDataGroupCategory) {
		getTurDataGroupCategories().remove(turDataGroupCategory);
		turDataGroupCategory.setTurMLCategory(null);

		return turDataGroupCategory;
	}

	public TurDataGroupSentence addTurDataSentence(TurDataGroupSentence turDataSentence) {
		getTurDataGroupSentences().add(turDataSentence);
		turDataSentence.setTurMLCategory(this);

		return turDataSentence;
	}

	public TurDataGroupSentence removeTurDataSentence(TurDataGroupSentence turDataSentence) {
		getTurDataGroupSentences().remove(turDataSentence);
		turDataSentence.setTurMLCategory(null);

		return turDataSentence;
	}

}