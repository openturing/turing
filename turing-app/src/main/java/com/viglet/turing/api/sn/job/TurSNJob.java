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

package com.viglet.turing.api.sn.job;

import java.io.Serializable;

import org.springframework.stereotype.Component;

@Component
public class TurSNJob implements Serializable {

	private static final long serialVersionUID = 1L;

	private String siteId;

	private TurSNJobItems turSNJobItems;

	public String getSiteId() {
		return siteId;
	}

	public void setSiteId(String siteId) {
		this.siteId = siteId;
	}

	public TurSNJobItems getTurSNJobItems() {
		return turSNJobItems;
	}

	public void setTurSNJobItems(TurSNJobItems turSNJobItems) {
		this.turSNJobItems = turSNJobItems;
	}
	
	public String toString() {
		return String.format("siteId: %s, turSNJobItems: %s", this.getSiteId(), this.getTurSNJobItems());
	}

}
