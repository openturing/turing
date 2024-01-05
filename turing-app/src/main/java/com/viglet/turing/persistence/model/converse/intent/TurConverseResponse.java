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

package com.viglet.turing.persistence.model.converse.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;

/**
 * The persistent class for the turMLModel database table.
 * 
 */
@Setter
@Getter
@Entity
@Table(name = "converse_response")
@JsonIgnoreProperties({ "intent" })
public class TurConverseResponse implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String text;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "intent_id")
	private TurConverseIntent intent;

	public TurConverseResponse() {
		super();
	}

	public TurConverseResponse(String text) {
		super();
		this.setText(text);
	}


}