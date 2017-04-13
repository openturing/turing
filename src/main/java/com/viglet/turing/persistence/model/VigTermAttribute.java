package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the vigTermAttribute database table.
 * 
 */
@Entity
@Table(name="vigTermAttribute")
@NamedQuery(name="VigTermAttribute.findAll", query="SELECT v FROM VigTermAttribute v")
public class VigTermAttribute implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=false, length=255)
	private String name;

	@Column(nullable=false, length=255)
	private String value;

	//bi-directional many-to-one association to VigTerm
	@ManyToOne
	@JoinColumn(name="term_id", nullable=false)
	private VigTerm vigTerm;

	public VigTermAttribute() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public VigTerm getVigTerm() {
		return this.vigTerm;
	}

	public void setVigTerm(VigTerm vigTerm) {
		this.vigTerm = vigTerm;
	}

}