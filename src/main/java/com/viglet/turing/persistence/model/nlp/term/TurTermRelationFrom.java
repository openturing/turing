package com.viglet.turing.persistence.model.nlp.term;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;


/**
 * The persistent class for the turTermRelationFrom database table.
 * 
 */
@Entity
@Table(name="turTermRelationFrom")
@NamedQuery(name="TurTermRelationFrom.findAll", query="SELECT trf FROM TurTermRelationFrom trf")
@JsonIgnoreProperties({ "turTerm" } )
public class TurTermRelationFrom implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(name="relation_type", nullable=false)
	private int relationType;

	//bi-directional many-to-one association to TurTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private TurTerm turTerm;

	//bi-directional many-to-one association to TurTermRelationTo
	@OneToMany(mappedBy="turTermRelationFrom")
	private List<TurTermRelationTo> turTermRelationTos;

	public TurTermRelationFrom() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getRelationType() {
		return this.relationType;
	}

	public void setRelationType(int relationType) {
		this.relationType = relationType;
	}

	public TurTerm getTurTerm() {
		return this.turTerm;
	}

	public void setTurTerm(TurTerm turTerm) {
		this.turTerm = turTerm;
	}

	public List<TurTermRelationTo> getTurTermRelationTos() {
		return this.turTermRelationTos;
	}

	public void setTurTermRelationTos(List<TurTermRelationTo> turTermRelationTos) {
		this.turTermRelationTos = turTermRelationTos;
	}

	public TurTermRelationTo addTurTermRelationTo(TurTermRelationTo turTermRelationTo) {
		getTurTermRelationTos().add(turTermRelationTo);
		turTermRelationTo.setTurTermRelationFrom(this);

		return turTermRelationTo;
	}

	public TurTermRelationTo removeTurTermRelationTo(TurTermRelationTo turTermRelationTo) {
		getTurTermRelationTos().remove(turTermRelationTo);
		turTermRelationTo.setTurTermRelationFrom(null);

		return turTermRelationTo;
	}

}