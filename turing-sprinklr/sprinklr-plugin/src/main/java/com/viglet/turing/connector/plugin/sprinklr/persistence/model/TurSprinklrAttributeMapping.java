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

package com.viglet.turing.connector.plugin.sprinklr.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.spring.jpa.TurUuid;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;


@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "sprinklr_attribute_mapping")
@JsonIgnoreProperties({ "turWCSource" })
public class TurSprinklrAttributeMapping implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurUuid
    @Column(name = "id", nullable = false)
    private String id;

    private String name;
    private String className;
    private String text;

    @ManyToOne
    @JoinColumn(name = "sprinklr_source_id", nullable = false)
    private TurSprinklrSource turSprinklrSource;

    public TurSprinklrAttributeMapping(String name, Class<?> className, TurSprinklrSource turSprinklrSource) {
        this.name = name;
        this.className = className.getName();
        this.text = null;
        this.turSprinklrSource = turSprinklrSource;
    }

    public TurSprinklrAttributeMapping(String name, String text, TurSprinklrSource turSprinklrSource) {
        this.name = name;
        this.className = null;
        this.text = text;
        this.turSprinklrSource = turSprinklrSource;
    }
}
