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

package com.viglet.turing.persistence.model.nlp.term;

import java.io.Serializable;
import javax.persistence.*;

import org.hibernate.annotations.GenericGenerator;


/**
 * The persistent class for the turTermAttribute database table.
 * 
 */
@Entity
@Table(name="turTermAttribute")
@NamedQuery(name="TurTermAttribute.findAll", query="SELECT ta FROM TurTermAttribute ta")
public class TurTermAttribute implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable=false, length=255)
	private String name;

	@Column(nullable=false, length=255)
	private String value;

	//bi-directional many-to-one association to TurTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private TurTerm turTerm;

	public TurTermAttribute() {
	}

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public TurTerm getTurTerm() {
		return this.turTerm;
	}

	public void setTurTerm(TurTerm turTerm) {
		this.turTerm = turTerm;
	}

}