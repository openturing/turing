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

package com.viglet.turing.client.sn.job;

import com.google.common.collect.Iterators;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * List of jobs to index and deIndex in Turing AI.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */
public class TurSNJobItems implements Iterable<TurSNJobItem>, Serializable {
	private static final long serialVersionUID = 1L;
	private List<TurSNJobItem> snJobItems = new ArrayList<>();
	public TurSNJobItems() {
		super();
	}
	public TurSNJobItems(TurSNJobItem turSNJobItem) {
		this.add(turSNJobItem);
	}
	public TurSNJobItems(List<TurSNJobItem> turSNJobItems) {
		turSNJobItems.forEach(this::add);
	}
	@Override
	public Iterator<TurSNJobItem> iterator() {
		return snJobItems.iterator();
	}
	public int size () {
		return Iterators.size(this.iterator());
	}
	public List<TurSNJobItem> getTuringDocuments() {
		return snJobItems;
	}

	public void setTuringDocuments(List<TurSNJobItem> jobItems) {
		this.snJobItems = jobItems;
	}

	public boolean add(TurSNJobItem turSNJobItem) {
		return snJobItems.add(turSNJobItem);
	}

	public boolean remove(TurSNJobItem turSNJobItem) {
		return snJobItems.remove(turSNJobItem);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (TurSNJobItem turSNJobItem : this) {
			sb.append(String.format("turSNJobItem: %s", turSNJobItem.toString()));
		}
		return sb.toString();
	}
}
