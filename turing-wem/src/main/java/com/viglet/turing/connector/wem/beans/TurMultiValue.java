/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.connector.wem.beans;

import java.util.ArrayList;
import java.util.List;

public class TurMultiValue extends ArrayList<String> {

	private static final long serialVersionUID = 1L;

	public static TurMultiValue singleItem(String text) {
		TurMultiValue turMultiValue = new TurMultiValue();
		turMultiValue.add(text);
		return turMultiValue;
	}


	public static TurMultiValue fromList(List<String> list) {
		TurMultiValue turMultiValue = new TurMultiValue();
        turMultiValue.addAll(list);
		return turMultiValue;
	}
}
