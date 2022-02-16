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

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.persistence.model.sn.TurSNSite;

import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

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

	@Column
	private String description;

	@Temporal(TemporalType.TIMESTAMP)
	private Date modificationDate;

	@Column
	private int managed = 1;

	@Column
	private String unmanagedId;

	@Column
	private String provider = "TURING";
	
	@Column
	private String language;

	// bi-directional many-to-one association to TurSNSite
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	@JsonBackReference(value = "turSNSiteSpotlight-turSNSite")
	private TurSNSite turSNSite;

	// bi-directional many-to-one association to turSNSiteSpotlightTerms
	@OneToMany(mappedBy = "turSNSiteSpotlight", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteSpotlightTerm> turSNSiteSpotlightTerms = new HashSet<>();

	// bi-directional many-to-one association to turSNSiteSpotlightDocuments
	@OneToMany(mappedBy = "turSNSiteSpotlight", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments = new HashSet<>();

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

	public int getManaged() {
		return managed;
	}

	public void setManaged(int managed) {
		this.managed = managed;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public Date getModificationDate() {
		return modificationDate;
	}

	public void setModificationDate(Date modificationDate) {
		this.modificationDate = modificationDate;
	}

	public String getUnmanagedId() {
		return unmanagedId;
	}

	public void setUnmanagedId(String unmanagedId) {
		this.unmanagedId = unmanagedId;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

}