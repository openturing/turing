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
import java.util.Map;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurNLPResponse {
	private TurNLPInstance turNLPInstance;
	private TurNLPVendor turNLPVendor;
	private List<TurNLPVendorEntity> turNLPVendorEntities;
	private Map<String, List<String>> entityMapWithProcessedValues;

	public TurNLPInstance getTurNLPInstance() {
		return turNLPInstance;
	}

	public void setTurNLPInstance(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;
	}

	public TurNLPVendor getTurNLPVendor() {
		return turNLPVendor;
	}

	public void setTurNLPVendor(TurNLPVendor turNLPVendor) {
		this.turNLPVendor = turNLPVendor;
	}

	public Map<String, List<String>> getEntityMapWithProcessedValues() {
		return entityMapWithProcessedValues;
	}

	public void setEntityMapWithProcessedValues(Map<String, List<String>> entityMapWithProcessedValues) {
		this.entityMapWithProcessedValues = entityMapWithProcessedValues;
	}

	public List<TurNLPVendorEntity> getTurNLPVendorEntities() {
		return turNLPVendorEntities;
	}

	public void setTurNLPVendorEntities(List<TurNLPVendorEntity> turNLPVendorEntities) {
		this.turNLPVendorEntities = turNLPVendorEntities;
	}

}
