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

package com.viglet.turing.exchange;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.viglet.turing.exchange.sn.TurSNSiteExchange;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TurExchange {

	@JsonInclude(Include.NON_NULL)
	private List<TurSNSiteExchange> snSites;

	public List<TurSNSiteExchange> getSnSites() {
		return snSites;
	}

	public void setSnSites(List<TurSNSiteExchange> snSites) {
		this.snSites = snSites;
	}



}
