package com.viglet.turing.persistence.model.ml;

import java.io.Serializable;
import javax.persistence.*;

import org.hibernate.annotations.Fetch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.storage.TurDataGroupCategory;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;

import java.util.List;


/**
 * The persistent class for the turCategory database table.
 * 
 */
@Entity
@Table(name="turMLCategory")
@NamedQuery(name="TurMLCategory.findAll", query="SELECT mlc FROM TurMLCategory mlc")
@JsonIgnoreProperties({ "turDataGroupCategories" })
public class TurMLCategory implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(length=255)
	private String description;

	@Column(name="internal_name", nullable=false, length=50)
	private String internalName;

	@Column(nullable=false, length=50)
	private String name;

	//bi-directional many-to-one association to TurDataGroupCategory
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turMLCategory", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurDataGroupCategory> turDataGroupCategories;

	//bi-directional many-to-one association to TurDataSentence
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "turMLCategory", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private List<TurDataGroupSentence> turDataGroupSentences;

	public TurMLCategory() {
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

	public String getInternalName() {
		return this.internalName;
	}

	public void setInternalName(String internalName) {
		this.internalName = internalName;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<TurDataGroupCategory> getTurDataGroupCategories() {
		return this.turDataGroupCategories;
	}

	public void setTurDataGroupCategories(List<TurDataGroupCategory> turDataGroupCategories) {
		this.turDataGroupCategories = turDataGroupCategories;
	}

	public TurDataGroupCategory addTurDataGroupCategory(TurDataGroupCategory turDataGroupCategory) {
		getTurDataGroupCategories().add(turDataGroupCategory);
		turDataGroupCategory.setTurMLCategory(this);

		return turDataGroupCategory;
	}

	public TurDataGroupCategory removeTurDataGroupCategory(TurDataGroupCategory turDataGroupCategory) {
		getTurDataGroupCategories().remove(turDataGroupCategory);
		turDataGroupCategory.setTurMLCategory(null);

		return turDataGroupCategory;
	}

	public List<TurDataGroupSentence> getTurDataGroupSentences() {
		return this.turDataGroupSentences;
	}

	public void setTurDataSentences(List<TurDataGroupSentence> turDataSentences) {
		this.turDataGroupSentences = turDataSentences;
	}

	public TurDataGroupSentence addTurDataSentence(TurDataGroupSentence turDataSentence) {
		getTurDataGroupSentences().add(turDataSentence);
		turDataSentence.setTurMLCategory(this);

		return turDataSentence;
	}

	public TurDataGroupSentence removeTurDataSentence(TurDataGroupSentence turDataSentence) {
		getTurDataGroupSentences().remove(turDataSentence);
		turDataSentence.setTurMLCategory(null);

		return turDataSentence;
	}

}