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

package com.viglet.turing.persistence.model.converse;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntity;
import com.viglet.turing.persistence.model.converse.intent.TurConverseContext;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import jakarta.persistence.Table;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The persistent class for the turConverseAgent database table.
 * 
 */
@Entity
@Table(name = "converse_agent")
@JsonIgnoreProperties({ "intents", "contexts", "chats" })
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class TurConverseAgent implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Setter
	@Getter
	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Setter
	@Getter
	@Column(nullable = false, length = 50)
	private String name;

	@Setter
	@Getter
	@Column(nullable = false, length = 200)
	private String description;

	@Setter
	@Getter
	@Column(nullable = false, length = 5)
	private String language;

	@Setter
	@Getter
	@Column(nullable = false, length = 50)
	private String core;

	@Getter
	@OneToMany(mappedBy = "agent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseIntent> intents = new HashSet<>();
	
	@Getter
	@OneToMany(mappedBy = "agent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseEntity> entities = new HashSet<>();
	
	@Getter
	@OneToMany(mappedBy = "agent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseContext> contexts = new HashSet<>();

	@Setter
	@Getter
	@ManyToOne
	@JoinColumn(name = "se_instance_id", nullable = false)
	private TurSEInstance turSEInstance;


	@OneToMany(mappedBy = "agent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private List<TurConverseChat> chats = new ArrayList<>();

	public void setIntents(Set<TurConverseIntent> intents) {
		this.intents.clear();
		if (intents != null) {
			this.intents.addAll(intents);
		}
	}

	public void setEntities(Set<TurConverseEntity> entities) {
		this.entities.clear();
		if (entities != null) {
			this.entities.addAll(entities);
		}
	}

	public void setContexts(Set<TurConverseContext> contexts) {
		this.contexts.clear();
		if (contexts != null) {
			this.contexts.addAll(contexts);
		}
	}
}
