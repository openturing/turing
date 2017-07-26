package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the vigNLPSolutions database table.
 * 
 */
@Entity
@Table(name="turNLPSolutions")
@NamedQuery(name="TurNLPSolution.findAll", query="SELECT v FROM TurNLPSolution v")
public class TurNLPSolution implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=true, length=255)
	private String description;

	@Column(nullable=true, length=255)
	private String plugin;

	@Column(nullable=false, length=100)
	private String title;

	@Column(nullable=true, length=255)
	private String website;

	public TurNLPSolution() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getPlugin() {
		return this.plugin;
	}

	public void setPlugin(String plugin) {
		this.plugin = plugin;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getWebsite() {
		return this.website;
	}

	public void setWebsite(String website) {
		this.website = website;
	}

}