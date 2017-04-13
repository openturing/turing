package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the vigDataSentence database table.
 * 
 */
@Entity
@Table(name="vigDataSentence")
@NamedQuery(name="VigDataSentence.findAll", query="SELECT v FROM VigDataSentence v")
public class VigDataSentence implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Lob
	@Column(nullable=false)
	private String sentence;

	//bi-directional many-to-one association to VigCategory
	@ManyToOne
	@JoinColumn(name="category_id")
	private VigCategory vigCategory;

	//bi-directional many-to-one association to VigData
	@ManyToOne
	@JoinColumn(name="data_id", nullable=false)
	private VigData vigData;

	public VigDataSentence() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getSentence() {
		return this.sentence;
	}

	public void setSentence(String sentence) {
		this.sentence = sentence;
	}

	public VigCategory getVigCategory() {
		return this.vigCategory;
	}

	public void setVigCategory(VigCategory vigCategory) {
		this.vigCategory = vigCategory;
	}

	public VigData getVigData() {
		return this.vigData;
	}

	public void setVigData(VigData vigData) {
		this.vigData = vigData;
	}

}