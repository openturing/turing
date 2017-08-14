package com.viglet.turing.persistence.model.nlp;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the vigNLPSolutions database table.
 * 
 */
@Entity
@Table(name="turNLPFeature")
@NamedQuery(name="TurNLPFeature.findAll", query="SELECT nf FROM TurNLPFeature nf")
public class TurNLPFeature implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=false, length=100)
	private String title;
	
	@Column(nullable=true, length=255)
	private String description;

	public TurNLPFeature() {
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

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

}