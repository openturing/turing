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

package com.viglet.turing.persistence.repository.sn.metric;

import java.util.Date;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurSNSiteMetricAccessTerm {

	private String term;
	private Date acessDate;

	public TurSNSiteMetricAccessTerm(String term, Date acessDate) {
		super();
		this.term = term;
		this.acessDate = acessDate;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public Date getAcessDate() {
		return acessDate;
	}

	public void setAcessDate(Date acessDate) {
		this.acessDate = acessDate;
	}

}
