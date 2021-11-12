/*
 * Copyright (C) 2016-2021 the original author or authors. 
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
package com.viglet.turing.plugins.nlp.opennlp;

import java.io.Serializable;

import opennlp.tools.namefind.NameFinderME;

public class TurNameFinderME implements Serializable {

	private static final long serialVersionUID = 1L;

	private transient NameFinderME nameFinderME;

	public TurNameFinderME(NameFinderME nameFinderME) {
		this.nameFinderME = nameFinderME;
	}

	public NameFinderME getNameFinderME() {
		return nameFinderME;
	}

	public void setNameFinderME(NameFinderME nameFinderME) {
		this.nameFinderME = nameFinderME;
	}

}
