package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the vigServicesNLPEntities database table.
 * 
 */
@Entity
@Table(name="turNLPEntity")
@NamedQuery(name="TurNLPEntity.findAll", query="SELECT ne FROM TurNLPEntity ne")
public class TurNLPEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=false)
	private int enabled;

	@Column(nullable=false, length=255)
	private String name;

	//bi-directional many-to-one association to VigEntity
	@ManyToOne
	@JoinColumn(name="entity_id", nullable=false)
	private TurEntity turEntity;

	@ManyToOne
	@JoinColumn(name="nlp_id", nullable=false)
	private TurNLP turNLP;

	public TurNLPEntity() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getEnabled() {
		return this.enabled;
	}

	public void setEnabled(int enabled) {
		this.enabled = enabled;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public TurEntity getTurEntity() {
		return this.turEntity;
	}

	public void setTurEntity(TurEntity turEntity) {
		this.turEntity = turEntity;
	}

	public TurNLP getTurNLP() {
		return this.turNLP;
	}

	public void setTurNLP(TurNLP turNLP) {
		this.turNLP = turNLP;
	}
}