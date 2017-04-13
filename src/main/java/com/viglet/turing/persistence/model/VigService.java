package com.viglet.turing.persistence.model;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the vigServices database table.
 * 
 */
@Entity
@Table(name="vigServices")
@NamedQuery(name="VigService.findAll", query="SELECT v FROM VigService v")
public class VigService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(unique=true, nullable=false)
	private int id;

	@Column(nullable=false, length=100)
	private String description;

	@Column(nullable=false)
	private int enabled;

	@Column(nullable=false, length=255)
	private String host;

	@Column(nullable=false, length=5)
	private String language;

	@Column(nullable=false)
	private int port;

	@Column(nullable=false)
	private int selected;

	@Column(name="`sub-type`", nullable=false)
	private int sub_type;

	@Column(nullable=false, length=100)
	private String title;

	@Column(nullable=false)
	private int type;

	//bi-directional many-to-one association to VigServicesNLPEntity
	@OneToMany(mappedBy="vigService")
	private List<VigServicesNLPEntity> vigServicesNlpentities;

	public VigService() {
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

	public int getSelected() {
		return this.selected;
	}

	public void setSelected(int selected) {
		this.selected = selected;
	}

	public int getSub_type() {
		return this.sub_type;
	}

	public void setSub_type(int sub_type) {
		this.sub_type = sub_type;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public int getType() {
		return this.type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public List<VigServicesNLPEntity> getVigServicesNlpentities() {
		return this.vigServicesNlpentities;
	}

	public void setVigServicesNlpentities(List<VigServicesNLPEntity> vigServicesNlpentities) {
		this.vigServicesNlpentities = vigServicesNlpentities;
	}

	public VigServicesNLPEntity addVigServicesNlpentity(VigServicesNLPEntity vigServicesNlpentity) {
		getVigServicesNlpentities().add(vigServicesNlpentity);
		vigServicesNlpentity.setVigService(this);

		return vigServicesNlpentity;
	}

	public VigServicesNLPEntity removeVigServicesNlpentity(VigServicesNLPEntity vigServicesNlpentity) {
		getVigServicesNlpentities().remove(vigServicesNlpentity);
		vigServicesNlpentity.setVigService(null);

		return vigServicesNlpentity;
	}

}