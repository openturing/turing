/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.persistence.model.sn.spotlight;

import java.io.Serializable;

import javax.persistence.*;

import org.hibernate.annotations.GenericGenerator;

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
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
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