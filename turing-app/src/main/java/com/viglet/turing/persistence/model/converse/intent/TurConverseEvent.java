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
import javax.persistence.*;

import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * The persistent class for the turMLModel database table.
 * 
 */
@Entity
@Table(name="turConverseEvent")
@NamedQuery(name="TurConverseEvent.findAll", query="SELECT ce FROM TurConverseEvent ce")
@JsonIgnoreProperties({ "intent" })
public class TurConverseEvent implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "intent_id")
	private TurConverseIntent intent;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public TurConverseIntent getIntent() {
		return intent;
	}

	public void setIntent(TurConverseIntent intent) {
		this.intent = intent;
	}
	

}