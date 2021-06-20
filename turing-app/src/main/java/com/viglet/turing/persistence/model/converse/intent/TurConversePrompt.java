/*
 * Copyright (C) 2019 the original author or authors. 
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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The persistent class for the turConversePrompt database table.
 * 
 */
@Entity
@Table(name = "turConversePrompt")
@NamedQuery(name = "TurConversePrompt.findAll", query = "SELECT cp FROM TurConversePrompt cp")
@JsonIgnoreProperties({ "parameter" })
public class TurConversePrompt implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private int position;

	private String text;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parameter_id")
	private TurConverseParameter parameter;

	public TurConversePrompt () {
		super();
	}
	
	public TurConversePrompt (int position, String text) {
		super();
		this.setPosition(position);
		this.setText(text);		
	}

	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public TurConverseParameter getParameter() {
		return parameter;
	}

	public void setParameter(TurConverseParameter parameter) {
		this.parameter = parameter;
	}

}
