/*
 * Copyright (C) 2016-2023 the original author or authors.
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

package com.viglet.turing.persistence.model.sn.ranking;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.spring.security.TurAuditable;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the turSNRankingExpression database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Getter
@Entity
@Table(name = "tur_sn_ranking_expression")
public class TurSNRankingExpression extends TurAuditable<String>  implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(length = 50)
	private String name;

	@Column(length = 255)
	private String description;
	@Column
	private float weight;

	// bi-directional many-to-one association to TurSNSite
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	private TurSNSite turSNSite;

	// bi-directional many-to-one association to turSNSiteLocales
	@OneToMany(mappedBy = "turSNRankingExpression", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN  })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNRankingCondition> turSNRankingConditions = new HashSet<>();

	public void setId(String id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setWeight(float weight) {
		this.weight = weight;
	}

	public void setTurSNSite(TurSNSite turSNSite) {
		this.turSNSite = turSNSite;
	}

	public void setTurSNRankingConditions(Set<TurSNRankingCondition> turSNRankingConditions) {
		this.turSNRankingConditions.clear();
		if (turSNRankingConditions != null) {
			this.turSNRankingConditions.addAll(turSNRankingConditions);
		}
	}

	public void setDescription(String description) {
		this.description = description;
	}
}