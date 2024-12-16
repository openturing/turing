package com.viglet.turing.persistence.model.auth;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.GenericGenerator;

/**
 * The persistent class for the TurGroup database table.
 * 
 */
@Entity
@NamedQuery(name = "TurGroup.findAll", query = "SELECT g FROM TurGroup g")
public class TurGroup implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")

	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String name;

	private String description;

	@ManyToMany(mappedBy = "turGroups")
	private Set<TurRole> turRoles = new HashSet<>();

	@ManyToMany(mappedBy = "turGroups")
	private Set<TurUser> turUsers = new HashSet<>();

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Set<TurUser> getTurUsers() {
		return this.turUsers;
	}

	public void setTurUsers(Set<TurUser> turUsers) {
		this.turUsers.clear();
		if (turUsers != null) {
			this.turUsers.addAll(turUsers);
		}
	}
}