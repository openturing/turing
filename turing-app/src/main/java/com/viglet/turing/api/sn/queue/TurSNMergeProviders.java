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
package com.viglet.turing.api.sn.queue;

import java.util.List;

import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * 
 * @author Alexandre Oliveira
 * @since 0.3.5
 *
 */
public class TurSNMergeProviders {
	private TurSNSite turSNSite;
	private String locale;
	private String fromProvider;
	private String toProvider;
	private String relationAttribFrom;
	private String relationAttribTo;
	private List<String> overwrittenFields;

	public TurSNSite getTurSNSite() {
		return turSNSite;
	}

	public void setTurSNSite(TurSNSite turSNSite) {
		this.turSNSite = turSNSite;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public String getFromProvider() {
		return fromProvider;
	}

	public void setFromProvider(String fromProvider) {
		this.fromProvider = fromProvider;
	}

	public String getToProvider() {
		return toProvider;
	}

	public void setToProvider(String toProvider) {
		this.toProvider = toProvider;
	}

	public String getRelationAttribFrom() {
		return relationAttribFrom;
	}

	public void setRelationAttribFrom(String relationAttribFrom) {
		this.relationAttribFrom = relationAttribFrom;
	}

	public String getRelationAttribTo() {
		return relationAttribTo;
	}

	public void setRelationAttribTo(String relationAttribTo) {
		this.relationAttribTo = relationAttribTo;
	}

	public List<String> getOverwrittenFields() {
		return overwrittenFields;
	}

	public void setOverwrittenFields(List<String> overwrittenFields) {
		this.overwrittenFields = overwrittenFields;
	}

}
