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

package com.viglet.turing.nlp;

import java.util.ArrayList;
import java.util.List;

public class TurNLPListKey<T> {
	private List<T> list;
	private StringBuilder hashCodeSb = new StringBuilder();

	public List<T> getList() {
		return list;
	}

	public void setList(List<T> list) {
		this.list = list;
	}

	@SuppressWarnings("unchecked")
	public TurNLPListKey(List<T> list) {
		this.list = (List<T>) ((ArrayList<T>) list).clone();
		for (int i = 0; i < this.list.size(); i++) {
			T item = this.list.get(i);
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
		return hashCodeSb.toString().equals(hashCodeObjectSb.toString());
	}

	@Override
	public String toString() {
		return hashCodeSb.toString();
	}

}