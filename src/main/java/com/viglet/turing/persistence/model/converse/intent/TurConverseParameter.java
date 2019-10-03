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

package com.viglet.turing.persistence.model.converse.intent;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The persistent class for the turConverseParameter database table.
 * 
 */
@Entity
@Table(name = "turConverseParameter")
@NamedQuery(name = "TurConverseParameter.findAll", query = "SELECT cp FROM TurConverseParameter cp")
@JsonIgnoreProperties({ "intent" })
public class TurConverseParameter implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private boolean required;

	private String name;

	private String entity;

	private String value;

	@ElementCollection
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@CollectionTable(name = "turConverseParameterPrompt")
	@JoinColumn(name = "parameter_id")
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<String> prompts = new HashSet<>();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "action_id")
	private TurConverseAction action;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEntity() {
		return entity;
	}

	public void setEntity(String entity) {
		this.entity = entity;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public Set<String> getPrompts() {
		return prompts;
	}

	public void setPrompts(Set<String> prompts) {
		this.prompts = prompts;
	}

	public TurConverseAction getAction() {
		return action;
	}

	public void setAction(TurConverseAction action) {
		this.action = action;
	}

}