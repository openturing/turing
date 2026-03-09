package com.viglet.turing.persistence.model.sn.source;

import java.io.Serial;
import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The persistent class for the TurSNSite database table.
 * 
 */
@Setter
@Getter
@Entity
@Table(name = "sn_source_type")
public class TurSNSourceType implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "id", updatable = false, nullable = false)
	private String id;
	@Column(nullable = false, length = 50)
	private String name;
	@Column(nullable = true, length = 255)
	private String description;

}
