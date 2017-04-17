package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Date;


/**
 * The persistent class for the VigOAuthRefreshTokens database table.
 * 
 */
@Entity
@Table(name="VigOAuthRefreshTokens")
@NamedQuery(name="VigOAuthRefreshToken.findAll", query="SELECT v FROM VigOAuthRefreshToken v")
public class VigOAuthRefreshToken implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private VigOAuthRefreshTokenPK id;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(nullable=false)
	private Date expires;

	@Column(nullable=false, length=50)
	private String scope;

	@Column(name="user_id", nullable=false, length=50)
	private String userId;

	public VigOAuthRefreshToken() {
	}

	public VigOAuthRefreshTokenPK getId() {
		return this.id;
	}

	public void setId(VigOAuthRefreshTokenPK id) {
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