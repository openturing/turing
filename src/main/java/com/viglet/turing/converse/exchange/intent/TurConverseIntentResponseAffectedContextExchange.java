/*
 * Copyright (C) 2019 the original author or authors. 
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

package com.viglet.turing.converse.exchange.intent;

public class TurConverseIntentResponseAffectedContextExchange {

	private String name;
	
	private TurConverseIntentResponseContextParameterExchange parameters;
	
	private int lifespan;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public TurConverseIntentResponseContextParameterExchange getParameters() {
		return parameters;
	}

	public void setParameters(TurConverseIntentResponseContextParameterExchange parameters) {
		this.parameters = parameters;
	}

	public int getLifespan() {
		return lifespan;
	}

	public void setLifespan(int lifespan) {
		this.lifespan = lifespan;
	}
	
}
