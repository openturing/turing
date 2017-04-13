package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the vigTermRelationFrom database table.
 * 
 */
@Entity
@Table(name="vigTermRelationFrom")
@NamedQuery(name="VigTermRelationFrom.findAll", query="SELECT v FROM VigTermRelationFrom v")
public class VigTermRelationFrom implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(name="relation_type", nullable=false)
	private int relationType;

	//bi-directional many-to-one association to VigTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private VigTerm vigTerm;

	//bi-directional many-to-one association to VigTermRelationTo
	@OneToMany(mappedBy="vigTermRelationFrom")
	private List<VigTermRelationTo> vigTermRelationTos;

	public VigTermRelationFrom() {
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

	public VigTerm getVigTerm() {
		return this.vigTerm;
	}

	public void setVigTerm(VigTerm vigTerm) {
		this.vigTerm = vigTerm;
	}

	public List<VigTermRelationTo> getVigTermRelationTos() {
		return this.vigTermRelationTos;
	}

	public void setVigTermRelationTos(List<VigTermRelationTo> vigTermRelationTos) {
		this.vigTermRelationTos = vigTermRelationTos;
	}

	public VigTermRelationTo addVigTermRelationTo(VigTermRelationTo vigTermRelationTo) {
		getVigTermRelationTos().add(vigTermRelationTo);
		vigTermRelationTo.setVigTermRelationFrom(this);

		return vigTermRelationTo;
	}

	public VigTermRelationTo removeVigTermRelationTo(VigTermRelationTo vigTermRelationTo) {
		getVigTermRelationTos().remove(vigTermRelationTo);
		vigTermRelationTo.setVigTermRelationFrom(null);

		return vigTermRelationTo;
	}

}