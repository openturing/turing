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
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;


/**
 * The persistent class for the turTermVariation database table.
 * 
 */
@Getter
@Setter
@Entity
@Table(name="term_variation")
@JsonIgnoreProperties({ "turTerm" } )
public class TurTermVariation implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable=false)
	private String name;

	@Column(name="name_lower", nullable=false)
	private String nameLower;

	@Column(name="rule_accent", nullable=false)
	private int ruleAccent;

	@Column(name="rule_case", nullable=false)
	private int ruleCase;

	@Column(name="rule_prefix")
	private String rulePrefix;

	@Column(name="rule_prefix_required")
	private int rulePrefixRequired;

	@Column(name="rule_suffix")
	private String ruleSuffix;

	@Column(name="rule_suffix_required")
	private int ruleSuffixRequired;

	@Column(nullable=false)
	private double weight;

	//bi-directional many-to-one association to TurTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private TurTerm turTerm;

	//bi-directional many-to-one association to TurTermVariationLanguage
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turTermVariation", cascade = CascadeType.ALL)
	private List<TurTermVariationLanguage> turTermVariationLanguages;
}