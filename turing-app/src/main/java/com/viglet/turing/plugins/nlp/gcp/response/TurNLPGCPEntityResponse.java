/*
 * Copyright (C) 2016-2022 the original author or authors. 
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
package com.viglet.turing.plugins.nlp.gcp.response;

import java.util.List;
import java.util.Map;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurNLPGCPEntityResponse {

	private String name;

	private TurNLPGCPEntityTypeResponse type;

	private Map<String, Object> metadata;

	private double salience;

	private List<TurNLPCGCPMentionResponse> mentions;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public TurNLPGCPEntityTypeResponse getType() {
		return type;
	}

	public void setType(TurNLPGCPEntityTypeResponse type) {
		this.type = type;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	public double getSalience() {
		return salience;
	}

	public void setSalience(double salience) {
		this.salience = salience;
	}

	public List<TurNLPCGCPMentionResponse> getMentions() {
		return mentions;
	}

	public void setMentions(List<TurNLPCGCPMentionResponse> mentions) {
		this.mentions = mentions;
	}

}
