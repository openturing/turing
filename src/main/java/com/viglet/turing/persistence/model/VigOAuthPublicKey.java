package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the VigOAuthPublicKeys database table.
 * 
 */
@Entity
@Table(name="VigOAuthPublicKeys")
@NamedQuery(name="VigOAuthPublicKey.findAll", query="SELECT v FROM VigOAuthPublicKey v")
public class VigOAuthPublicKey implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="client_id", unique=true, nullable=false, length=50)
	private String clientId;

	@Column(name="encryption_algorithm", nullable=false, length=80)
	private String encryptionAlgorithm;

	@Column(name="private_key", nullable=false, length=8000)
	private String privateKey;

	@Column(name="public_key", nullable=false, length=8000)
	private String publicKey;

	public VigOAuthPublicKey() {
	}

	public String getClientId() {
		return this.clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getEncryptionAlgorithm() {
		return this.encryptionAlgorithm;
	}

	public void setEncryptionAlgorithm(String encryptionAlgorithm) {
		this.encryptionAlgorithm = encryptionAlgorithm;
	}

	public String getPrivateKey() {
		return this.privateKey;
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}

	public String getPublicKey() {
		return this.publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

}