/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.persistence.model.system;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The persistent class for the vigNLPSolutions database table.
 * 
 */
@Entity
@Table(name = "turLocale")
@NamedQuery(name = "TurLocale.findAll", query = "SELECT l FROM TurLocale l")
public class TurLocale implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@Column(unique = true, nullable = false, length = 5)
	private String initials;

	@Column(nullable = true, length = 255)
	private String en;

	@Column(nullable = true, length = 255)
	private String pt;

	public TurLocale(String initials, String en, String pt) {
		setInitials(initials);
		setEn(en);
		setPt(pt);
	}

	public TurLocale() {
		super();
	}

	public String getInitials() {
		return initials;
	}

	public void setInitials(String initials) {
		this.initials = initials;
	}

	public String getEn() {
		return en;
	}

	public void setEn(String en) {
		this.en = en;
	}

	public String getPt() {
		return pt;
	}

	public void setPt(String pt) {
		this.pt = pt;
	}

}