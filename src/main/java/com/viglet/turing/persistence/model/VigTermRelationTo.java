package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the vigTermRelationTo database table.
 * 
 */
@Entity
@Table(name="vigTermRelationTo")
@NamedQuery(name="VigTermRelationTo.findAll", query="SELECT v FROM VigTermRelationTo v")
public class VigTermRelationTo implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	//bi-directional many-to-one association to VigTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private VigTerm vigTerm;

	//bi-directional many-to-one association to VigTermRelationFrom
	@ManyToOne
	@JoinColumn(name="relation_from_id", nullable=false)
	private VigTermRelationFrom vigTermRelationFrom;

	public VigTermRelationTo() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public VigTerm getVigTerm() {
		return this.vigTerm;
	}

	public void setVigTerm(VigTerm vigTerm) {
		this.vigTerm = vigTerm;
	}

	public VigTermRelationFrom getVigTermRelationFrom() {
		return this.vigTermRelationFrom;
	}

	public void setVigTermRelationFrom(VigTermRelationFrom vigTermRelationFrom) {
		this.vigTermRelationFrom = vigTermRelationFrom;
	}

}