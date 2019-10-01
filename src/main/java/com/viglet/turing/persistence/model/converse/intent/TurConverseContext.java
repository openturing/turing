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
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The persistent class for the turMLModel database table.
 * 
 */
@Entity
@Table(name = "turConverseContext")
@NamedQuery(name = "TurConverseContext.findAll", query = "SELECT cc FROM TurConverseContext cc")
@JsonIgnoreProperties({ "intentInputs", "intentOutputs" })
public class TurConverseContext implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String text;

	@ManyToMany
	private Set<TurConverseIntent> intentInputs = new HashSet<>();

	@ManyToMany
	private Set<TurConverseIntent> intentOutputs = new HashSet<>();

	public TurConverseContext() {
		super();
	}

	public TurConverseContext(String text) {
		super();
		this.setText(text);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public Set<TurConverseIntent> getIntentInputs() {
		return this.intentInputs;
	}

	public void setIntentInputs(Set<TurConverseIntent> intentInputs) {
		this.intentInputs.clear();
		if (intentInputs != null) {
			this.intentInputs.addAll(intentInputs);
		}
	}

	public Set<TurConverseIntent> getIntentOutputs() {
		return this.intentOutputs;
	}

	public void setIntentOutputs(Set<TurConverseIntent> intentOutputs) {
		this.intentOutputs.clear();
		if (intentOutputs != null) {
			this.intentOutputs.addAll(intentOutputs);
		}
	}

}