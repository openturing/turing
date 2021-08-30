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
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * The persistent class for the turSNSiteSpotlight database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.4
 */
@Entity
@Table(name = "turSNSiteSpotlight")
@NamedQuery(name = "TurSNSiteSpotlight.findAll", query = "SELECT snss FROM TurSNSiteSpotlight snss")
public class TurSNSiteSpotlight implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 50)
	private String name;
	
	@Column(nullable = true, length = 255)
	private String description;

	// bi-directional many-to-one association to TurSNSite
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	@JsonBackReference (value="turSNSiteSpotlight-turSNSite")
	private TurSNSite turSNSite;
	
	// bi-directional many-to-one association to TurSNSiteFieldExt
	@OneToMany(mappedBy = "turSNSiteSpotlight", orphanRemoval = true)
	@Cascade({ CascadeType.ALL })
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteSpotlightTerm> turSNSiteSpotlightTerms;
	
	// bi-directional many-to-one association to TurSNSiteFieldExt
	@OneToMany(mappedBy = "turSNSiteSpotlight", orphanRemoval = true)
	@Cascade({ CascadeType.ALL })
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public TurSNSite getTurSNSite() {
		return turSNSite;
	}

	public void setTurSNSite(TurSNSite turSNSite) {
		this.turSNSite = turSNSite;
	}

	public Set<TurSNSiteSpotlightDocument> getTurSNSiteSpotlightDocuments() {
		return turSNSiteSpotlightDocuments;
	}

	public void setTurSNSiteSpotlightDocuments(Set<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments) {
		this.turSNSiteSpotlightDocuments = turSNSiteSpotlightDocuments;
	}

	public Set<TurSNSiteSpotlightTerm> getTurSNSiteSpotlightTerms() {
		return turSNSiteSpotlightTerms;
	}

	public void setTurSNSiteSpotlightTerms(Set<TurSNSiteSpotlightTerm> turSNSiteSpotlightTerms) {
		this.turSNSiteSpotlightTerms = turSNSiteSpotlightTerms;
	}


}