/*
 * Copyright (C) 2016-2021 the original author or authors. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.client.sn;

import java.util.Date;

/**
 * Create a query and specify between dates.
 * 
 * @since 0.3.4
 */
public class TurSNClientBetweenDates {
	
	private String field;
	
	private Date startDate;
	
	private Date endDate;

	public TurSNClientBetweenDates(String field, Date startDate, Date endDate) {
		this.setField(field);
		this.setStartDate(startDate);
		this.setEndDate(endDate);
	}

	public String getField() {
		return field;
	}


	public void setField(String field) {
		this.field = field;
	}


	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}
	
	
}
