/*
 * Copyright (C) 2016-2022 the original author or authors. 
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
package com.viglet.turing.nlp;

import java.util.List;

import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurNLPEntityRequest {

	private String name;
	private List<String> types;
	private List<String> subTypes;
	private TurNLPVendorEntity turNLPVendorEntity;

	public TurNLPEntityRequest(String name, List<String> types, List<String> subTypes,
			TurNLPVendorEntity turNLPVendorEntity) {
		super();
		this.name = name;
		this.types = types;
		this.subTypes = subTypes;
		this.turNLPVendorEntity = turNLPVendorEntity;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<String> getTypes() {
		return types;
	}

	public void setTypes(List<String> types) {
		this.types = types;
	}

	public List<String> getSubTypes() {
		return subTypes;
	}

	public void setSubTypes(List<String> subTypes) {
		this.subTypes = subTypes;
	}

	public TurNLPVendorEntity getTurNLPVendorEntity() {
		return turNLPVendorEntity;
	}

	public void setTurNLPVendorEntity(TurNLPVendorEntity turNLPVendorEntity) {
		this.turNLPVendorEntity = turNLPVendorEntity;
	}

}
