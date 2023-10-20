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

package com.viglet.turing.api.sn.job;


import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class TurSNJobItems implements Iterable<TurSNJobItem>, Serializable {
	@Serial
	private static final long serialVersionUID = 1L;
	private List<TurSNJobItem> jobItems = new ArrayList<>();

	@Override
	public @NotNull Iterator<TurSNJobItem> iterator() {
		return jobItems.iterator();
	}

	public List<TurSNJobItem> getTuringDocuments() {
		return jobItems;
	}

	public void setTuringDocuments(List<TurSNJobItem> jobItems) {
		this.jobItems = jobItems;
	}

	public boolean add(TurSNJobItem turSNJobItem) {
		return jobItems.add(turSNJobItem);
	}

	public boolean remove(TurSNJobItem turSNJobItem) {
		return jobItems.remove(turSNJobItem);
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (TurSNJobItem turSNJobItem : this){
			sb.append(String.format("turSNJobItem: %s", turSNJobItem.toString()));
		}
		return sb.toString();
	}
}
