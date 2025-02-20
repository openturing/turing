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

package com.viglet.turing.persistence.model.sn.locale;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

/**
 * The persistent class for the turSNSiteLocale database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Getter
@Setter
@Entity
@Table(name = "sn_site_locale")
public class TurSNSiteLocale implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 5)
	private Locale language;

	@Column(nullable = false, length = 50)
	private String core;

	// bi-directional many-to-one association to TurSNSite
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	private TurSNSite turSNSite;
}