package com.viglet.turing.persistence.model.sn;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.se.TurSEInstance;

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
	
	@Column(nullable = false, length = 50)
	private String facetName;

	@Column(nullable = false, length = 255)
	private String description;

	@Column(nullable = false, length = 50)
	private String type;

	@Column(nullable = false)
	private int multiValued;
	
	@Column(nullable = false)
	private int facet;
	
	@Column(nullable = false)
	private int hl;
	
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

	public String getFacetName() {
		return facetName;
	}

	public void setFacetName(String facetName) {
		this.facetName = facetName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
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

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public int getFacet() {
		return facet;
	}

	public void setFacet(int facet) {
		this.facet = facet;
	}

	public int getHl() {
		return hl;
	}

	public void setHl(int hl) {
		this.hl = hl;
	}

	
}