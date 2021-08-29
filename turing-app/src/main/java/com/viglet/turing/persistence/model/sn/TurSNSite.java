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

package com.viglet.turing.persistence.model.sn;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;

/**
 * The persistent class for the vigServices database table.
 * 
 */
@Entity
@Table(name = "turSNSite")
@NamedQuery(name = "TurSNSite.findAll", query = "SELECT sns FROM TurSNSite sns")
public class TurSNSite implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, length = 255)
	private String description;

	@Column(nullable = false, length = 5)
	private String language;

	@Column(nullable = false, length = 50)
	private String core;

	@Column(nullable = true)
	private int rowsPerPage;

	@Column(nullable = true)
	private int facet;

	@Column(nullable = true)
	private int itemsPerFacet;

	@Column(nullable = true)
	private int hl;

	@Column(nullable = true, length = 50)
	private String hlPre;

	@Column(nullable = true, length = 50)
	private String hlPost;

	@Column(nullable = true)
	private int mlt;

	@Column(nullable = true)
	private int thesaurus;

	@Column(nullable = true)
	private String defaultTitleField;

	@Column(nullable = true)
	private String defaultTextField;

	@Column(nullable = true)
	private String defaultDescriptionField;

	@Column(nullable = true)
	private String defaultDateField;

	@Column(nullable = true)
	private String defaultImageField;

	@Column(nullable = true)
	private String defaultURLField;

	// bi-directional many-to-one association to TurSEInstance
	@ManyToOne
	@JoinColumn(name = "se_instance_id", nullable = false)
	private TurSEInstance turSEInstance;

	// bi-directional many-to-one association to TurNLPInstance
	@ManyToOne
	@JoinColumn(name = "nlp_instance_id", nullable = true)
	private TurNLPInstance turNLPInstance;

	// bi-directional many-to-one association to TurSNSiteField
	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true)
	@Cascade({ CascadeType.ALL })
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteField> turSNSiteFields;

	// bi-directional many-to-one association to TurSNSiteFieldExt
	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true)
	@Cascade({ CascadeType.ALL })
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteFieldExt> turSNSiteFieldExts;

	// bi-directional many-to-one association to TurSNSiteFieldExt
	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true)
	@Cascade({ CascadeType.ALL })
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteSpotlight> turSNSiteSpotlights;
	
	public TurSNSite() {
	}

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

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public TurSEInstance getTurSEInstance() {
		return turSEInstance;
	}

	public void setTurSEInstance(TurSEInstance turSEInstance) {
		this.turSEInstance = turSEInstance;
	}

	public TurNLPInstance getTurNLPInstance() {
		return turNLPInstance;
	}

	public void setTurNLPInstance(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;
	}

	public Set<TurSNSiteField> getTurSNSiteFields() {
		return turSNSiteFields;
	}

	public void setTurSNSiteFields(Set<TurSNSiteField> turSNSiteFields) {
		this.turSNSiteFields = turSNSiteFields;
	}

	public TurSNSiteField addTurSNSiteField(TurSNSiteField turSNSiteField) {
		getTurSNSiteFields().add(turSNSiteField);
		turSNSiteField.setTurSNSite(this);

		return turSNSiteField;
	}

	public TurSNSiteField removeTurNLPInstanceEntity(TurSNSiteField turSNSiteField) {
		getTurSNSiteFields().remove(turSNSiteField);
		turSNSiteField.setTurSNSite(this);

		return turSNSiteField;
	}

	public String getCore() {
		return core;
	}

	public void setCore(String core) {
		this.core = core;
	}

	public int getRowsPerPage() {
		return rowsPerPage;
	}

	public void setRowsPerPage(int rowsPerPage) {
		this.rowsPerPage = rowsPerPage;
	}

	public int getFacet() {
		return facet;
	}

	public void setFacet(int facet) {
		this.facet = facet;
	}

	public int getItemsPerFacet() {
		return itemsPerFacet;
	}

	public void setItemsPerFacet(int itemsPerFacet) {
		this.itemsPerFacet = itemsPerFacet;
	}

	public int getHl() {
		return hl;
	}

	public void setHl(int hl) {
		this.hl = hl;
	}

	public String getHlPre() {
		return hlPre;
	}

	public void setHlPre(String hlPre) {
		this.hlPre = hlPre;
	}

	public String getHlPost() {
		return hlPost;
	}

	public void setHlPost(String hlPost) {
		this.hlPost = hlPost;
	}

	public int getMlt() {
		return mlt;
	}

	public void setMlt(int mlt) {
		this.mlt = mlt;
	}

	public int getThesaurus() {
		return thesaurus;
	}

	public void setThesaurus(int thesaurus) {
		this.thesaurus = thesaurus;
	}

	public String getDefaultTextField() {
		return defaultTextField;
	}

	public void setDefaultTextField(String defaultTextField) {
		this.defaultTextField = defaultTextField;
	}

	public String getDefaultDescriptionField() {
		return defaultDescriptionField;
	}

	public void setDefaultDescriptionField(String defaultDescriptionField) {
		this.defaultDescriptionField = defaultDescriptionField;
	}

	public String getDefaultDateField() {
		return defaultDateField;
	}

	public void setDefaultDateField(String defaultDateField) {
		this.defaultDateField = defaultDateField;
	}

	public String getDefaultURLField() {
		return defaultURLField;
	}

	public void setDefaultURLField(String defaultURLField) {
		this.defaultURLField = defaultURLField;
	}

	public String getDefaultTitleField() {
		return defaultTitleField;
	}

	public void setDefaultTitleField(String defaultTitleField) {
		this.defaultTitleField = defaultTitleField;
	}

	public String getDefaultImageField() {
		return defaultImageField;
	}

	public void setDefaultImageField(String defaultImageField) {
		this.defaultImageField = defaultImageField;
	}

}