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

package com.viglet.turing.persistence.model.sn.spotlight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;

/**
 * The persistent class for the turSNSiteSpotlightDocument database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.4
 */
@Getter
@Entity
@Table(name = "tur_sn_site_spotlight_document")
@JsonIgnoreProperties({ "turSNSiteSpotlight" })
public class TurSNSiteSpotlightDocument implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(length = 50)
	private int position;

	@Column
	private String title;

	@Column
	private String type;

	@Column
	private String referenceId;

	@Column(length = 2000)
	private String content;

	@Column
	private String link;

	// bi-directional many-to-one association to TurSNSiteSpotlight
	@ManyToOne
	@JoinColumn(name = "spotlight_id", nullable = false)
	private TurSNSiteSpotlight turSNSiteSpotlight;

	public void setId(String id) {
		this.id = id;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setReferenceId(String referenceId) {
		this.referenceId = referenceId;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public void setTurSNSiteSpotlight(TurSNSiteSpotlight turSNSiteSpotlight) {
		this.turSNSiteSpotlight = turSNSiteSpotlight;
	}

}