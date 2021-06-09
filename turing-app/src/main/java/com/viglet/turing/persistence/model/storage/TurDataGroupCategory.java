/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.persistence.model.storage;

import java.io.Serializable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.persistence.model.ml.TurMLCategory;


/**
 * The persistent class for the turDataGroupCategory database table.
 * 
 */
@Entity
@Table(name="turDataGroupCategory")
@NamedQuery(name="TurDataGroupCategory.findAll", query="SELECT dgc FROM TurDataGroupCategory dgc")
public class TurDataGroupCategory implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator = "id_Sequence")
    @SequenceGenerator(name = "id_Sequence", sequenceName = "ID_SEQ")
	@Column(unique=true, nullable=false)
	private int id;

	//bi-directional many-to-one association to TurCategory
	@ManyToOne
	@JoinColumn(name="ml_category_id", nullable=false)
	private TurMLCategory turMLCategory;

	//bi-directional many-to-one association to TurDataGroup
	@ManyToOne
	@JoinColumn(name="data_group_id", nullable=false)
	@JsonBackReference (value="turDataGroupCategory-turDataGroup")
	private TurDataGroup turDataGroup;

	public TurDataGroupCategory() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public TurMLCategory getTurMLCategory() {
		return this.turMLCategory;
	}

	public void setTurMLCategory(TurMLCategory turCategory) {
		this.turMLCategory = turCategory;
	}

	public TurDataGroup getTurDataGroup() {
		return this.turDataGroup;
	}

	public void setTurDataGroup(TurDataGroup turDataGroup) {
		this.turDataGroup = turDataGroup;
	}

}