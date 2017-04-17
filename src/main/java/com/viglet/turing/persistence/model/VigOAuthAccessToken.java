package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Date;


/**
 * The persistent class for the VigOAuthAccessTokens database table.
 * 
 */
@Entity
@Table(name="VigOAuthAccessTokens")
@NamedQuery(name="VigOAuthAccessToken.findAll", query="SELECT v FROM VigOAuthAccessToken v")
public class VigOAuthAccessToken implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private VigOAuthAccessTokenPK id;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(nullable=false)
	private Date expires;

	@Column(length=50)
	private String scope;

	@Column(name="user_id", nullable=false, length=50)
	private String userId;

	public VigOAuthAccessToken() {
	}

	public VigOAuthAccessTokenPK getId() {
		return this.id;
	}

	public void setId(VigOAuthAccessTokenPK id) {
		this.id = id;
	}

	public Date getExpires() {
		return this.expires;
	}

	public void setExpires(Date expires) {
		this.expires = expires;
	}

	public String getScope() {
		return this.scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public String getUserId() {
		return this.userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

}