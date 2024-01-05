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

package com.viglet.turing.persistence.model.nlp.term;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;


/**
 * The persistent class for the turTerm database table.
 * 
 */
@Getter
@Setter
@Entity
@Table(name="term")
@JsonIgnoreProperties({ "turNLPEntity" } )
public class TurTerm implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
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
}