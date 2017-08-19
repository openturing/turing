package com.viglet.turing.persistence.model.ml;

import java.io.Serializable;
import javax.persistence.*;

import com.viglet.turing.persistence.service.system.TurConfigVarService;

/**
 * The persistent class for the vigServices database table.
 * 
 */
@Entity
@Table(name = "turMLInstance")
@NamedQuery(name = "TurMLInstance.findAll", query = "SELECT mi FROM TurMLInstance mi")
public class TurMLInstance implements Serializable {
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
	
	// bi-directional many-to-one association to VigService
	@ManyToOne(cascade = { CascadeType.REMOVE }, fetch = FetchType.LAZY)
	@JoinColumn(name = "ml_vendor_id", nullable = false)
	private TurMLVendor turMLVendor;

	@Transient
	private boolean isSelected;

	public TurMLInstance() {
	}

	public boolean isSelected() {
		TurConfigVarService turConfigVarService = new TurConfigVarService();
		if (Integer.parseInt(turConfigVarService.get("DEFAULT_ML").getValue()) == this.getId()) {
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

	public TurMLVendor getTurMLVendor() {
		return turMLVendor;
	}

	public void setTurMLVendor(TurMLVendor turMLVendor) {
		this.turMLVendor = turMLVendor;
	}
}