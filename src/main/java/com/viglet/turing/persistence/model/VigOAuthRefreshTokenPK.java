package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the VigOAuthRefreshTokens database table.
 * 
 */
@Embeddable
public class VigOAuthRefreshTokenPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="refresh_token", unique=true, nullable=false, length=40)
	private String refreshToken;

	@Column(name="client_id", unique=true, nullable=false, length=50)
	private String clientId;

	public VigOAuthRefreshTokenPK() {
	}
	public String getRefreshToken() {
		return this.refreshToken;
	}
	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
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
		if (!(other instanceof VigOAuthRefreshTokenPK)) {
			return false;
		}
		VigOAuthRefreshTokenPK castOther = (VigOAuthRefreshTokenPK)other;
		return 
			this.refreshToken.equals(castOther.refreshToken)
			&& this.clientId.equals(castOther.clientId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.refreshToken.hashCode();
		hash = hash * prime + this.clientId.hashCode();
		
		return hash;
	}
}