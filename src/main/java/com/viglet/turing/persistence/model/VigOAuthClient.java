package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the VigOAuthClients database table.
 * 
 */
@Entity
@Table(name="VigOAuthClients")
@NamedQuery(name="VigOAuthClient.findAll", query="SELECT v FROM VigOAuthClient v")
public class VigOAuthClient implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="client_id", unique=true, nullable=false, length=50)
	private String clientId;

	@Column(name="client_secret", nullable=false, length=20)
	private String clientSecret;

	@Column(name="redirect_uri", nullable=false, length=255)
	private String redirectUri;

	public VigOAuthClient() {
	}

	public String getClientId() {
		return this.clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getClientSecret() {
		return this.clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	public String getRedirectUri() {
		return this.redirectUri;
	}

	public void setRedirectUri(String redirectUri) {
		this.redirectUri = redirectUri;
	}

}