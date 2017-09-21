package com.viglet.turing.persistence.model.system;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The persistent class for the vigNLPSolutions database table.
 * 
 */
@Entity
@Table(name = "turConfigVar")
@NamedQuery(name = "TurConfigVar.findAll", query = "SELECT cv FROM TurConfigVar cv")
public class TurConfigVar implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@Column(unique = true, nullable = false, length = 25)
	private String id;

	@Column(nullable = true, length = 255)
	private String path;

	@Column(nullable = true, length = 255)
	private String value;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}


}