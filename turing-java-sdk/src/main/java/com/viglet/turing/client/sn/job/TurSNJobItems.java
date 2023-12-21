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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * List of jobs to index and deindex in Turing AI.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */
public class TurSNJobItems implements Iterable<TurSNJobItem>, Serializable {
	private static final long serialVersionUID = 1L;
	private Queue<TurSNJobItem> turSNJobItems = new ConcurrentLinkedQueue<>();
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
		return turSNJobItems.iterator();
	}
	public Queue<TurSNJobItem>  getTuringDocuments() {
		return turSNJobItems;
	}

	public void setTuringDocuments(Queue<TurSNJobItem>  turSNJobItems) {
		this.turSNJobItems = turSNJobItems;
	}

	public boolean add(TurSNJobItem turSNJobItem) {
		return turSNJobItems.add(turSNJobItem);
	}

	public boolean remove(TurSNJobItem turSNJobItem) {
		return turSNJobItems.remove(turSNJobItem);
	}
}
