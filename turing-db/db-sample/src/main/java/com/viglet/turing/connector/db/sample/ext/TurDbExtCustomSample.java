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
package com.viglet.turing.connector.db.sample.ext;

import com.viglet.turing.connector.db.ext.TurDbExtCustomImpl;
import java.sql.Connection;
import java.util.Map;

/**
*
* @author Alexandre Oliveira
* 
* @since 0.3.0
*
**/
public class TurDbExtCustomSample implements TurDbExtCustomImpl {
	private static final String TITLE = "title";
	
	@Override
	public Map<String, Object> run(Connection connection, Map<String, Object> attributes) {
		if (attributes.containsKey(TITLE))
			attributes.replace(TITLE, String.format("Sample: %s", attributes.get(TITLE)));
		return attributes;
	}

}
