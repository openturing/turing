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

package com.viglet.turing.client.sn.spotlight;

import com.viglet.turing.commons.sn.bean.TurSNSiteSpotlightDocumentBean;

/**
 * Spotlight Document.
 * 
 * @since 0.3.5
 */
public class TurSNSpotlightDocument {

	private String id;

	private int position;

	private String title;

	private String type;

	private String referenceId;

	private String content;

	private String link;

	public TurSNSpotlightDocument(TurSNSiteSpotlightDocumentBean turSNSiteSpotlightDocumentBean) {
		this.id = turSNSiteSpotlightDocumentBean.getId();
		this.position = turSNSiteSpotlightDocumentBean.getPosition();
		this.title = turSNSiteSpotlightDocumentBean.getTitle();
		this.type = turSNSiteSpotlightDocumentBean.getType();
		this.referenceId = turSNSiteSpotlightDocumentBean.getReferenceId();
		this.content = turSNSiteSpotlightDocumentBean.getContent();
		this.link = turSNSiteSpotlightDocumentBean.getLink();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getReferenceId() {
		return referenceId;
	}

	public void setReferenceId(String referenceId) {
		this.referenceId = referenceId;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

}
