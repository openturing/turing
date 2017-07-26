package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;

/**
 * The persistent class for the vigServices database table.
 * 
 */
@Entity
@Table(name = "turNLP")
@NamedQuery(name = "TurNLP.findAll", query = "SELECT n FROM TurNLP n")
public class TurNLP implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(unique = true, nullable = false)
	private int id;
	
	@Column(nullable = false, length = 100)
	private String title;
	
	@Column(nullable = false, length = 100)
	private String description;

	@Column(nullable = false)
	private int enabled;

	@Column(nullable = false, length = 255)
	private String host;

	@Column(nullable = false, length = 5)
	private String language;

	@Column(nullable = false)
	private int port;

	@Column(nullable = false)
	private int selected;

	// bi-directional many-to-one association to VigService
	@ManyToOne
	@JoinColumn(name = "nlp_solution_id", nullable = false)
	private TurNLPSolution turNLPSolution ;



	// bi-directional many-to-one association to VigServicesNLPEntity
	@OneToMany(mappedBy = "turNLP")
	private List<TurNLPEntity> turNLPEntities;

	public TurNLP() {
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

	public int getEnabled() {
		return this.enabled;
	}

	public void setEnabled(int enabled) {
		this.enabled = enabled;
	}

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getLanguage() {
		return this.language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getSelected() {
		return this.selected;
	}

	public void setSelected(int selected) {
		this.selected = selected;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public List<TurNLPEntity> getTurNLPEntities() {
		return this.turNLPEntities;
	}

	public void setVigNLPEntities(List<TurNLPEntity> turNLPEntities) {
		this.turNLPEntities = turNLPEntities;
	}

	public TurNLPEntity addVigServicesNLPEntity(TurNLPEntity turNLPEntity) {
		getTurNLPEntities().add(turNLPEntity);
		turNLPEntity.setTurNLP(this);

		return turNLPEntity;
	}

	public TurNLPEntity removeVigServicesNlPEntity(TurNLPEntity turNLPEntity) {
		getTurNLPEntities().remove(turNLPEntity);
		turNLPEntity.setTurNLP(null);

		return turNLPEntity;
	}

}