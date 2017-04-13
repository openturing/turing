package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the vigData database table.
 * 
 */
@Entity
@Table(name="vigData")
@NamedQuery(name="VigData.findAll", query="SELECT v FROM VigData v")
public class VigData implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=false, length=50)
	private String name;

	@Column(nullable=false, length=50)
	private String type;

	//bi-directional many-to-one association to VigDataGroupData
	@OneToMany(mappedBy="vigData")
	private List<VigDataGroupData> vigDataGroupData;

	//bi-directional many-to-one association to VigDataSentence
	@OneToMany(mappedBy="vigData")
	private List<VigDataSentence> vigDataSentences;

	public VigData() {
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

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public List<VigDataGroupData> getVigDataGroupData() {
		return this.vigDataGroupData;
	}

	public void setVigDataGroupData(List<VigDataGroupData> vigDataGroupData) {
		this.vigDataGroupData = vigDataGroupData;
	}

	public VigDataGroupData addVigDataGroupData(VigDataGroupData vigDataGroupData) {
		getVigDataGroupData().add(vigDataGroupData);
		vigDataGroupData.setVigData(this);

		return vigDataGroupData;
	}

	public VigDataGroupData removeVigDataGroupData(VigDataGroupData vigDataGroupData) {
		getVigDataGroupData().remove(vigDataGroupData);
		vigDataGroupData.setVigData(null);

		return vigDataGroupData;
	}

	public List<VigDataSentence> getVigDataSentences() {
		return this.vigDataSentences;
	}

	public void setVigDataSentences(List<VigDataSentence> vigDataSentences) {
		this.vigDataSentences = vigDataSentences;
	}

	public VigDataSentence addVigDataSentence(VigDataSentence vigDataSentence) {
		getVigDataSentences().add(vigDataSentence);
		vigDataSentence.setVigData(this);

		return vigDataSentence;
	}

	public VigDataSentence removeVigDataSentence(VigDataSentence vigDataSentence) {
		getVigDataSentences().remove(vigDataSentence);
		vigDataSentence.setVigData(null);

		return vigDataSentence;
	}

}