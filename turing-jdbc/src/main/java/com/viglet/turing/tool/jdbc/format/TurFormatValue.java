/*
 * Copyright (C) 2017-2022 the original author or authors. 
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
package com.viglet.turing.tool.jdbc.format;

import org.jsoup.Jsoup;

import com.viglet.turing.tool.jdbc.TurJDBCImportTool;

/**
*
* @author Alexandre Oliveira
* @since 0.3.0
*
**/
public class TurFormatValue {
	private static final String ID = "id";
	private TurJDBCImportTool jdbcImportTool = null;
	
	public TurFormatValue(TurJDBCImportTool jdbcImportTool) {
		this.jdbcImportTool = jdbcImportTool;
	}
	public String format(String name, String value) {
		String[] strHTMLFields = jdbcImportTool.getHtmlField().toLowerCase().split(",");
		for (String strHTMLField : strHTMLFields) {
			if (name.equalsIgnoreCase(strHTMLField.toLowerCase())) {
				if (name.equalsIgnoreCase(ID)) {
					this.idField(Jsoup.parse(value).text());

				} else {
					return Jsoup.parse(value).text();
				}
			}
		}
		if (name.equalsIgnoreCase(ID)) {
			return this.idField(value);
		} else {
			return value;
		}
	}

	public String idField(int idValue) {
		if (jdbcImportTool.isTypeInId()) {
			return jdbcImportTool.type + idValue;
		} else {
			return Integer.toString(idValue);
		}
	}

	public String idField(String idValue) {
		if (jdbcImportTool.isTypeInId()) {
			return jdbcImportTool.type + idValue;
		} else {
			return idValue;
		}
	}

}
