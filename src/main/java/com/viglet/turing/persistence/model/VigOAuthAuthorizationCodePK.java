package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the VigOAuthAuthorizationCodes database table.
 * 
 */
@Embeddable
public class VigOAuthAuthorizationCodePK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="authorization_code", unique=true, nullable=false, length=40)
	private String authorizationCode;

	@Column(name="client_id", unique=true, nullable=false, length=50)
	private String clientId;

	public VigOAuthAuthorizationCodePK() {
	}
	public String getAuthorizationCode() {
		return this.authorizationCode;
	}
	public void setAuthorizationCode(String authorizationCode) {
		this.authorizationCode = authorizationCode;
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
		if (!(other instanceof VigOAuthAuthorizationCodePK)) {
			return false;
		}
		VigOAuthAuthorizationCodePK castOther = (VigOAuthAuthorizationCodePK)other;
		return 
			this.authorizationCode.equals(castOther.authorizationCode)
			&& this.clientId.equals(castOther.clientId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.authorizationCode.hashCode();
		hash = hash * prime + this.clientId.hashCode();
		
		return hash;
	}
}