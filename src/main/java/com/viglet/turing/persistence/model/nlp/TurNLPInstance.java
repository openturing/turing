package com.viglet.turing.persistence.model.nlp;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.service.system.TurConfigVarService;

import java.util.List;

/**
 * The persistent class for the vigServices database table.
 * 
 */
@Entity
@Table(name = "turNLPInstance")
@NamedQuery(name = "TurNLPInstance.findAll", query = "SELECT n FROM TurNLPInstance n")
@JsonIgnoreProperties({ "turNLPInstanceEntities" })
public class TurNLPInstance implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(unique = true, nullable = false)
	private int id;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 100)
	private String description;

	@Column(nullable = false)
	private int enabled;

	@Column(nullable = false, length = 255)
	private String host;

	@Column(nullable = false, length = 5)
	private String language;

	@Column(nullable = false)
	private int port;

	// bi-directional many-to-one association to TurNLPVendor
	@ManyToOne
	@JoinColumn(name = "nlp_vendor_id", nullable = false)
	private TurNLPVendor turNLPVendor;

	// bi-directional many-to-one association to TurNLPInstanceEntity
	@OneToMany(mappedBy = "turNLPInstance")
	private List<TurNLPInstanceEntity> turNLPInstanceEntities;

	@Transient
	private boolean isSelected;

	public TurNLPInstance() {
	}

	public boolean isSelected() {
		TurConfigVarService turConfigVarService = new TurConfigVarService();
		if (Integer.parseInt(turConfigVarService.get("DEFAULT_NLP").getValue()) == this.getId()) {
			isSelected = true;
		} else {
			isSelected = false;
		}
		return isSelected;
	}

	public void setSelected(boolean isSelected) {
		this.isSelected = isSelected;
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

	public int getEnabled() {
		return this.enabled;
	}

	public void setEnabled(int enabled) {
		this.enabled = enabled;
	}

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getLanguage() {
		return this.language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}
	
	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public TurNLPVendor getTurNLPVendor() {
		return turNLPVendor;
	}

	public void setTurNLPVendor(TurNLPVendor turNLPVendor) {
		this.turNLPVendor = turNLPVendor;
	}

	public List<TurNLPInstanceEntity> getTurNLPInstanceEntities() {
		return this.turNLPInstanceEntities;
	}

	public void setVigNLPInstanceEntities(List<TurNLPInstanceEntity> turNLPInstanceEntities) {
		this.turNLPInstanceEntities = turNLPInstanceEntities;
	}

	public TurNLPInstanceEntity addVigServicesNLPEntity(TurNLPInstanceEntity turNLPInstanceEntity) {
		getTurNLPInstanceEntities().add(turNLPInstanceEntity);
		turNLPInstanceEntity.setTurNLPInstance(this);

		return turNLPInstanceEntity;
	}

	public TurNLPInstanceEntity removeVigServicesNlPEntity(TurNLPInstanceEntity turNLPInstanceEntity) {
		getTurNLPInstanceEntities().remove(turNLPInstanceEntity);
		turNLPInstanceEntity.setTurNLPInstance(null);

		return turNLPInstanceEntity;
	}

}