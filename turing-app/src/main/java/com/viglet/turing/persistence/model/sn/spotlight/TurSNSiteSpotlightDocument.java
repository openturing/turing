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

import java.io.Serializable;

import jakarta.persistence.*;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The persistent class for the turSNSiteSpotlightDocument database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.4
 */
@Entity
@Table(name = "turSNSiteSpotlightDocument")
@NamedQuery(name = "TurSNSiteSpotlightDocument.findAll", query = "SELECT snssd FROM TurSNSiteSpotlightDocument snssd")
@JsonIgnoreProperties({ "turSNSiteSpotlight" })
public class TurSNSiteSpotlightDocument implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = true, length = 50)
	private int position;

	@Column(nullable = true, length = 255)
	private String title;

	@Column(nullable = true, length = 255)
	private String type;

	@Column(nullable = true, length = 255)
	private String referenceId;

	@Column(nullable = true, length = 2000)
	private String content;

	@Column(nullable = true, length = 255)
	private String link;

	// bi-directional many-to-one association to TurSNSiteSpotlight
	@ManyToOne(fetch = FetchType.LAZY) // (cascade = {CascadeType.ALL})
	@JoinColumn(name = "sn_site_spotlight_id")
	private TurSNSiteSpotlight turSNSiteSpotlight;

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

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getReferenceId() {
		return referenceId;
	}

	public void setReferenceId(String referenceId) {
		this.referenceId = referenceId;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public TurSNSiteSpotlight getTurSNSiteSpotlight() {
		return turSNSiteSpotlight;
	}

	public void setTurSNSiteSpotlight(TurSNSiteSpotlight turSNSiteSpotlight) {
		this.turSNSiteSpotlight = turSNSiteSpotlight;
	}

}