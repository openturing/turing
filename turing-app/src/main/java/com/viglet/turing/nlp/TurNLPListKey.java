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

package com.viglet.turing.nlp;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class TurNLPListKey<T> {
	@Getter
	private List<T> list;
	private final StringBuilder hashCodeSb = new StringBuilder();

	public void setList(List<T> list) {
		this.list = list;
	}

	@SuppressWarnings("unchecked")
	public TurNLPListKey(List<T> list) {
		this.list = (List<T>) ((ArrayList<T>) list).clone();
        for (T item : this.list) {
            hashCodeSb.append(item);
            hashCodeSb.append(",");
        }
	}

	@Override
	public int hashCode() {
		return hashCodeSb.toString().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		StringBuilder hashCodeObjectSb = new StringBuilder();
		@SuppressWarnings("unchecked")
		TurNLPListKey<T> objList = (TurNLPListKey<T>) obj;

		objList.list.forEach(item -> {
			hashCodeObjectSb.append(item);
			hashCodeObjectSb.append(",");
		});
		return hashCodeSb.toString().contentEquals(hashCodeObjectSb);
	}

	@Override
	public String toString() {
		return hashCodeSb.toString();
	}

}