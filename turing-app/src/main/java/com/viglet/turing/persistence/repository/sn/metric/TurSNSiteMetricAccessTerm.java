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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
	private long total;
	private double numFound;

	public TurSNSiteMetricAccessTerm(String term, Date acessDate) {
		super();
		this.term = term;
		this.acessDate = acessDate;
	}

	public TurSNSiteMetricAccessTerm(String term, long total, double numFound) {
		super();
		this.term = term;
		this.total = total;
		this.numFound =  new BigDecimal(numFound).setScale(0, RoundingMode.HALF_UP).doubleValue();
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

	public long getTotal() {
		return total;
	}

	public void setTotal(long total) {
		this.total = total;
	}

	public double getNumFound() {
		return numFound;
	}

	public void setNumFound(double numFound) {
		this.numFound = numFound;
	}

}
