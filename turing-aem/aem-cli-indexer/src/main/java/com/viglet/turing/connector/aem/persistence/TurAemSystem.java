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

package com.viglet.turing.connector.aem.persistence;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.*;

@RequiredArgsConstructor
@Accessors(chain = true)
@Setter
@Getter
@Entity
@Table(name="aem_system",
        uniqueConstraints={@UniqueConstraint(columnNames={"config"})})
public class TurAemSystem {
    @Id
    private String config;
    @Column
    private String stringValue;
    @Column
    private boolean booleanValue;

    public TurAemSystem(String config, String value) {
        this.config = config;
        this.stringValue = value;
    }

    public TurAemSystem(String config, boolean value) {
        this.config = config;
        this.booleanValue = value;
    }
}
