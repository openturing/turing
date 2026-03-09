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

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;
import com.viglet.turing.spring.security.TurAuditable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * The persistent class for the turSNRankingExpression database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Getter
@Entity
@ToString
@Table(name = "sn_ranking_expression")
public class TurSNRankingExpression extends TurAuditable<String> implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Setter
	@Id
	@TurAssignableUuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Setter
	@Column(length = 50)
	private String name;

	@Setter
	@Column
	private String description;
	@Setter
	@Column
	private float weight;

	@Setter
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	private TurSNSite turSNSite;

	@OneToMany(mappedBy = "turSNRankingExpression", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNRankingCondition> turSNRankingConditions = new HashSet<>();

	public void setTurSNRankingConditions(Set<TurSNRankingCondition> turSNRankingConditions) {
		this.turSNRankingConditions.clear();
		if (turSNRankingConditions != null) {
			for (TurSNRankingCondition turSNRankingCondition : turSNRankingConditions) {
				turSNRankingCondition.setTurSNRankingExpression(this);
				this.turSNRankingConditions.add(turSNRankingCondition);
			}
		}
	}
}