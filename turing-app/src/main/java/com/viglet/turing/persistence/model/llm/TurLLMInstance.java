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
package com.viglet.turing.persistence.model.llm;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Set;

/**
 * The persistent class for the TurLLMInstance database table.
 * 
 */
@Getter
@Setter
@Entity
@Table(name = "llm_instance")
public class TurLLMInstance implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 100)
	private String description;

	@Column(nullable = false)
	private int enabled;

	@Column(nullable = false)
	private String url;

	@ManyToOne
	@JoinColumn(name = "llm_vendor_id", nullable = false)
	private TurLLMVendor turLLMVendor;

	@Column
	private String modelName;

	@Column
	private Double temperature;

	@Column
	private Integer topK;

	@Column
	private Double topP;

	@Column
	private Double repeatPenalty;

	@Column
	private Integer seed;

	@Column
	private Integer numPredict;

	@Column
	private String stop;

	@Column
	private String responseFormat;

	@Column
	private String supportedCapabilities;

	@Column
	private String timeout;

	@Column
	private Integer maxRetries;
}