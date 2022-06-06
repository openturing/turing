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
package com.viglet.turing.persistence.model.storage;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonBackReference;

/**
 * The persistent class for the turDataGroupData database table.
 * 
 */
@Entity
@Table(name="turDataGroupData")
@NamedQuery(name="TurDataGroupData.findAll", query="SELECT dgd FROM TurDataGroupData dgd")
public class TurDataGroupData implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator = "id_Sequence")
    @SequenceGenerator(name = "id_Sequence", sequenceName = "ID_SEQ")
	@Column(unique=true, nullable=false)
	private int id;

	//bi-directional many-to-one association to TurData
	@ManyToOne
	@JoinColumn(name="data_id", nullable=false)
	private TurData turData;

	//bi-directional many-to-one association to TurDataGroup
	@ManyToOne
	@JoinColumn(name="data_group_id", nullable=false)
	@JsonBackReference (value="turDataGroupData-turDataGroup")
	private TurDataGroup turDataGroup;

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public TurData getTurData() {
		return this.turData;
	}

	public void setTurData(TurData turData) {
		this.turData = turData;
	}

	public TurDataGroup getTurDataGroup() {
		return this.turDataGroup;
	}

	public void setTurDataGroup(TurDataGroup turDataGroup) {
		this.turDataGroup = turDataGroup;
	}

}