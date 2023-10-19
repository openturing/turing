package com.viglet.turing.properties;

public class TurSolrProperty {

	private int timeout;
	private boolean cloud;

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public boolean isCloud() {
		return cloud;
	}

	public void setCloud(boolean cloud) {
		this.cloud = cloud;
	}
}
