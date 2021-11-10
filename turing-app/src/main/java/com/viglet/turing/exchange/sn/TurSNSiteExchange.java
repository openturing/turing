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

package com.viglet.turing.exchange.sn;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteExchange {

	private String id;

	private String name;

	private String description;

	private String language;

	private String core;

	private int rowsPerPage;

	private boolean facet;

	private int itemsPerFacet;

	private boolean hl;

	private String hlPre;

	private String hlPost;

	private boolean mlt;

	private boolean thesaurus;

	private String defaultTitleField;

	private String defaultTextField;

	private String defaultDescriptionField;

	private String defaultDateField;

	private String defaultImageField;

	private String defaultURLField;

	private String turSEInstance;

	private String turNLPInstance;

	private Set<TurSNSiteField> turSNSiteFields;

	public TurSNSiteExchange() {
		super();
	}

	public TurSNSiteExchange(TurSNSite turSNSite) {
		this.setCore(turSNSite.getCore());
		this.setDefaultDateField(turSNSite.getDefaultDateField());
		this.setDefaultDescriptionField(turSNSite.getDefaultDescriptionField());
		this.setDefaultImageField(turSNSite.getDefaultImageField());
		this.setDefaultTextField(turSNSite.getDefaultTextField());
		this.setDefaultTitleField(turSNSite.getDefaultTitleField());
		this.setDefaultURLField(turSNSite.getDefaultURLField());
		this.setDescription(turSNSite.getDescription());
		this.setFacet(turSNSite.getFacet() == 1);
		this.setHl(turSNSite.getHl() == 1);
		this.setHlPost(turSNSite.getHlPost());
		this.setHlPre(turSNSite.getHlPre());
		this.setId(turSNSite.getId());
		this.setItemsPerFacet(turSNSite.getItemsPerFacet());
		this.setLanguage(turSNSite.getLanguage());
		this.setMlt(turSNSite.getMlt() == 1);
		this.setName(turSNSite.getName());
		this.setRowsPerPage(turSNSite.getRowsPerPage());
		this.setThesaurus(turSNSite.getThesaurus() == 1);
		if (turSNSite.getTurNLPInstance() != null) {
			this.setTurNLPInstance(turSNSite.getTurNLPInstance().getId());
		}
		if (turSNSite.getTurSEInstance() != null) {
			this.setTurSEInstance(turSNSite.getTurSEInstance().getId());
		}
		this.setTurSNSiteFields(turSNSite.getTurSNSiteFields());
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

	public boolean getFacet() {
		return facet;
	}

	public void setFacet(boolean facet) {
		this.facet = facet;
	}

	public int getItemsPerFacet() {
		return itemsPerFacet;
	}

	public void setItemsPerFacet(int itemsPerFacet) {
		this.itemsPerFacet = itemsPerFacet;
	}

	public boolean getHl() {
		return hl;
	}

	public void setHl(boolean hl) {
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

	public boolean getMlt() {
		return mlt;
	}

	public void setMlt(boolean mlt) {
		this.mlt = mlt;
	}

	public boolean getThesaurus() {
		return thesaurus;
	}

	public void setThesaurus(boolean thesaurus) {
		this.thesaurus = thesaurus;
	}

	public String getDefaultTitleField() {
		return defaultTitleField;
	}

	public void setDefaultTitleField(String defaultTitleField) {
		this.defaultTitleField = defaultTitleField;
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

	public String getDefaultImageField() {
		return defaultImageField;
	}

	public void setDefaultImageField(String defaultImageField) {
		this.defaultImageField = defaultImageField;
	}

	public String getDefaultURLField() {
		return defaultURLField;
	}

	public void setDefaultURLField(String defaultURLField) {
		this.defaultURLField = defaultURLField;
	}

	public String getTurSEInstance() {
		return turSEInstance;
	}

	public void setTurSEInstance(String turSEInstance) {
		this.turSEInstance = turSEInstance;
	}

	public String getTurNLPInstance() {
		return turNLPInstance;
	}

	public void setTurNLPInstance(String turNLPInstance) {
		this.turNLPInstance = turNLPInstance;
	}

	public Set<TurSNSiteField> getTurSNSiteFields() {
		return turSNSiteFields;
	}

	public void setTurSNSiteFields(Set<TurSNSiteField> turSNSiteFields) {
		this.turSNSiteFields = turSNSiteFields;
	}

}