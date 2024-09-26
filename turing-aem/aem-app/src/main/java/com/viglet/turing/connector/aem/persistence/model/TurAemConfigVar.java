/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.aem.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * The persistent class for the vigNLPSolutions database table.
 */
@Setter
@Getter
@Entity
@Table(name = "aem_config")
public class TurAemConfigVar implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(unique = true, nullable = false, length = 250)
    private String id;

    @Column
    private String path;

    @Lob
    private String value;

    public TurAemConfigVar() {
        super();
    }

    public TurAemConfigVar(String id, String path, String value) {
        this.id = id;
        this.path = path;
        this.value = value;
    }

}
