package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the VigOAuthScopes database table.
 * 
 */
@Entity
@Table(name="VigOAuthScopes")
@NamedQuery(name="VigOAuthScope.findAll", query="SELECT v FROM VigOAuthScope v")
public class VigOAuthScope implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false, length=50)
	private String scope;

	@Column(name="is_default", nullable=false)
	private byte isDefault;

	public VigOAuthScope() {
	}

	public String getScope() {
		return this.scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public byte getIsDefault() {
		return this.isDefault;
	}

	public void setIsDefault(byte isDefault) {
		this.isDefault = isDefault;
	}

}