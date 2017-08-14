package com.viglet.turing.persistence.model.storage;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the turData database table.
 * 
 */
@Entity
@Table(name="turData")
@NamedQuery(name="TurData.findAll", query="SELECT d FROM TurData d")
public class TurData implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=false, length=50)
	private String name;

	@Column(nullable=false, length=50)
	private String type;

	//bi-directional many-to-one association to TurDataGroupData
	@OneToMany(mappedBy="turData")
	private List<TurDataGroupData> turDataGroupData;

	//bi-directional many-to-one association to TurDataSentence
	@OneToMany(mappedBy="turData")
	private List<TurDataSentence> turDataSentences;

	public TurData() {
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

	public List<TurDataGroupData> getTurDataGroupData() {
		return this.turDataGroupData;
	}

	public void setTurDataGroupData(List<TurDataGroupData> turDataGroupData) {
		this.turDataGroupData = turDataGroupData;
	}

	public TurDataGroupData addTurDataGroupData(TurDataGroupData turDataGroupData) {
		getTurDataGroupData().add(turDataGroupData);
		turDataGroupData.setTurData(this);

		return turDataGroupData;
	}

	public TurDataGroupData removeTurDataGroupData(TurDataGroupData turDataGroupData) {
		getTurDataGroupData().remove(turDataGroupData);
		turDataGroupData.setTurData(null);

		return turDataGroupData;
	}

	public List<TurDataSentence> getTurDataSentences() {
		return this.turDataSentences;
	}

	public void setTurDataSentences(List<TurDataSentence> turDataSentences) {
		this.turDataSentences = turDataSentences;
	}

	public TurDataSentence addTurDataSentence(TurDataSentence turDataSentence) {
		getTurDataSentences().add(turDataSentence);
		turDataSentence.setTurData(this);

		return turDataSentence;
	}

	public TurDataSentence removeTurDataSentence(TurDataSentence turDataSentence) {
		getTurDataSentences().remove(turDataSentence);
		turDataSentence.setTurData(null);

		return turDataSentence;
	}

}