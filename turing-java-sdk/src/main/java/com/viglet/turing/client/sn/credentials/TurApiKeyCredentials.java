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

package com.viglet.turing.client.sn.credentials;

import lombok.Getter;
import lombok.Setter;

/**
 * Turing Server Credentials.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.5
 */
@Setter
@Getter
public class TurApiKeyCredentials implements TurCredentials {

	private String apiKey;

	public TurApiKeyCredentials(String apiKey) {
		super();
		this.apiKey = apiKey;
	}

}
