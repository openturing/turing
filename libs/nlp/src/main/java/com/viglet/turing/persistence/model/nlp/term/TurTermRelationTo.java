package com.viglet.turing.persistence.model.nlp.term;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * The persistent class for the turTermRelationTo database table.
 * 
 */
@Entity
@Table(name="turTermRelationTo")
@NamedQuery(name="TurTermRelationTo.findAll", query="SELECT trt FROM TurTermRelationTo trt")
@JsonIgnoreProperties({ "turTerm", "turTermRelationFrom" } )
public class TurTermRelationTo implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	//bi-directional many-to-one association to TurTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private TurTerm turTerm;

	//bi-directional many-to-one association to TurTermRelationFrom
	@ManyToOne
	@JoinColumn(name="relation_from_id", nullable=false)
	private TurTermRelationFrom turTermRelationFrom;

	public TurTermRelationTo() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public TurTerm getTurTerm() {
		return this.turTerm;
	}

	public void setTurTerm(TurTerm turTerm) {
		this.turTerm = turTerm;
	}

	public TurTermRelationFrom getTurTermRelationFrom() {
		return this.turTermRelationFrom;
	}

	public void setTurTermRelationFrom(TurTermRelationFrom turTermRelationFrom) {
		this.turTermRelationFrom = turTermRelationFrom;
	}

}