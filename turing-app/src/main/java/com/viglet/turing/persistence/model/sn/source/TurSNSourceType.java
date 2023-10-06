package com.viglet.turing.persistence.model.sn.source;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;

/**
 * The persistent class for the TurSNSite database table.
 * 
 */
@Entity
@Table(name = "turSNSourceType")
@NamedQuery(name = "TurSNSourceType.findAll", query = "SELECT sns FROM TurSNSourceType sns")
public class TurSNSourceType implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
	
	@Id
	@Column(name = "id", updatable = false, nullable = false)
	private String id;
	@Column(nullable = false, length = 50)
	private String name;
	@Column(nullable = false, length = 255)
	private String description;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
