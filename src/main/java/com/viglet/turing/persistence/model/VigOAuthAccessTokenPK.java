package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the VigOAuthAccessTokens database table.
 * 
 */
@Embeddable
public class VigOAuthAccessTokenPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="access_token", unique=true, nullable=false, length=40)
	private String accessToken;

	@Column(name="client_id", unique=true, nullable=false, length=50)
	private String clientId;

	public VigOAuthAccessTokenPK() {
	}
	public String getAccessToken() {
		return this.accessToken;
	}
	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}
	public String getClientId() {
		return this.clientId;
	}
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof VigOAuthAccessTokenPK)) {
			return false;
		}
		VigOAuthAccessTokenPK castOther = (VigOAuthAccessTokenPK)other;
		return 
			this.accessToken.equals(castOther.accessToken)
			&& this.clientId.equals(castOther.clientId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.accessToken.hashCode();
		hash = hash * prime + this.clientId.hashCode();
		
		return hash;
	}
}