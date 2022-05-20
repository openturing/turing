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
package com.viglet.turing.plugins.nlp.gcp.request;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurNLPGCPDocumentRequest {

	private TurNLPGCPTypeResponse type;

	private String language;

	private String content;
	
	private String gcsContentUri;

	public TurNLPGCPDocumentRequest(TurNLPGCPTypeResponse type, String language, String content) {
		super();
		this.type = type;
		this.language = language;
		this.content = content;
	}

	public TurNLPGCPTypeResponse getType() {
		return type;
	}

	public void setType(TurNLPGCPTypeResponse type) {
		this.type = type;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getGcsContentUri() {
		return gcsContentUri;
	}

	public void setGcsContentUri(String gcsContentUri) {
		this.gcsContentUri = gcsContentUri;
	}

	
}
