package com.viglet.turing.properties;

import lombok.Getter;

@Getter
public class TurSolrProperty {

	private int timeout;
	private boolean cloud;

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public void setCloud(boolean cloud) {
		this.cloud = cloud;
	}
}
