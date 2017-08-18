package com.viglet.turing.persistence.model.sn;

import java.io.Serializable;
import javax.persistence.*;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.se.TurSEInstance;

/**
 * The persistent class for the vigServices database table.
 * 
 */
@Entity
@Table(name = "turSNSite")
@NamedQuery(name = "TurSNSite.findAll", query = "SELECT sns FROM TurSNSite sns")
public class TurSNSite implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(unique = true, nullable = false)
	private int id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, length = 255)
	private String description;

	@Column(nullable = false, length = 5)
	private String language;

	// bi-directional many-to-one association to TurSEInstance
	@ManyToOne
	@JoinColumn(name = "se_instance_id", nullable = false)
	private TurSEInstance turSEInstance;

	// bi-directional many-to-one association to TurNLPInstance
	@ManyToOne
	@JoinColumn(name = "nlp_instance_id", nullable = false)
	private TurNLPInstance turNLPInstance;

	public TurSNSite() {
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public TurSEInstance getTurSEInstance() {
		return turSEInstance;
	}

	public void setTurSEInstance(TurSEInstance turSEInstance) {
		this.turSEInstance = turSEInstance;
	}

	public TurNLPInstance getTurNLPInstance() {
		return turNLPInstance;
	}

	public void setTurNLPInstance(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;
	}

}