package com.viglet.turing.persistence.model.sn;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.se.field.TurSEFieldType;

/**
 * The persistent class for the vigServices database table.
 * 
 */
@Entity
@Table(name = "turSNSiteField")
@NamedQuery(name = "TurSNSiteField.findAll", query = "SELECT snsf FROM TurSNSiteField snsf")
public class TurSNSiteField implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(unique = true, nullable = false)
	private int id;

	@Column(nullable = false, length = 50)
	private String name;
	
	@Column(nullable = true, length = 255)
	private String description;

	@Column(nullable = false)
	private TurSEFieldType type;

	@Column(nullable = true)
	private int multiValued;
	
	// bi-directional many-to-one association to TurSNSite
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	@JsonBackReference (value="turSNSiteField-turSNSite")
	private TurSNSite turSNSite;

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

	public TurSEFieldType getType() {
		return type;
	}

	public void setType(TurSEFieldType type) {
		this.type = type;
	}

	public int getMultiValued() {
		return multiValued;
	}

	public void setMultiValued(int multiValued) {
		this.multiValued = multiValued;
	}

	public TurSNSite getTurSNSite() {
		return turSNSite;
	}

	public void setTurSNSite(TurSNSite turSNSite) {
		this.turSNSite = turSNSite;
	}

	
}