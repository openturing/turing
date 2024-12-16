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

package com.viglet.turing.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;

@Component
public class TurSNSiteFieldUtils {
	@Autowired
	private TurSNSiteFieldRepository turSNSiteFieldRepository;
	
	public Map<String, TurSNSiteField> toMap(TurSNSite turSNSite) {
		List<TurSNSiteField> turSNSiteFields = turSNSiteFieldRepository.findByTurSNSite(turSNSite);
		Map<String, TurSNSiteField> turSNSiteFieldsMap = new HashMap<>();
		for (TurSNSiteField turSNSiteField : turSNSiteFields)
			turSNSiteFieldsMap.put(turSNSiteField.getName(), turSNSiteField);

		return turSNSiteFieldsMap;

	}
}