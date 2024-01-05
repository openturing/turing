package com.viglet.turing.persistence.model.sn.source;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

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
	@Column(nullable = false, length = 255)
	private String description;

}
