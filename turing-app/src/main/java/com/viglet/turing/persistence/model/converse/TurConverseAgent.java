/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.persistence.model.converse;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntity;
import com.viglet.turing.persistence.model.converse.intent.TurConverseContext;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.se.TurSEInstance;

/**
 * The persistent class for the turConverseAgent database table.
 * 
 */
@Entity
@Table(name = "turConverseAgent")
@NamedQuery(name = "TurConverseAgent.findAll", query = "SELECT ca FROM TurConverseAgent ca")
@JsonIgnoreProperties({ "intents", "contexts", "chats" })
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class TurConverseAgent implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, length = 200)
	private String description;

	@Column(nullable = false, length = 5)
	private String language;

	@Column(nullable = false, length = 50)
	private String core;

	@OneToMany(mappedBy = "agent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseIntent> intents = new HashSet<>();
	
	@OneToMany(mappedBy = "agent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseEntity> entities = new HashSet<>();
	
	@OneToMany(mappedBy = "agent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseContext> contexts = new HashSet<>();

	@ManyToOne
	@JoinColumn(name = "se_instance_id", nullable = false)
	private TurSEInstance turSEInstance;


	@OneToMany(mappedBy = "agent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private List<TurConverseChat> chats = new ArrayList<>();
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getCore() {
		return core;
	}

	public void setCore(String core) {
		this.core = core;
	}

	public TurSEInstance getTurSEInstance() {
		return turSEInstance;
	}

	public void setTurSEInstance(TurSEInstance turSEInstance) {
		this.turSEInstance = turSEInstance;
	}

	public Set<TurConverseIntent> getIntents() {
		return this.intents;
	}

	public void setIntents(Set<TurConverseIntent> intents) {
		this.intents.clear();
		if (intents != null) {
			this.intents.addAll(intents);
		}
	}

	public Set<TurConverseEntity> getEntities() {
		return this.entities;
	}

	public void setEntities(Set<TurConverseEntity> entities) {
		this.entities.clear();
		if (entities != null) {
			this.entities.addAll(entities);
		}
	}
	
	public Set<TurConverseContext> getContexts() {
		return this.contexts;
	}

	public void setContexts(Set<TurConverseContext> contexts) {
		this.contexts.clear();
		if (contexts != null) {
			this.contexts.addAll(contexts);
		}
	}
}