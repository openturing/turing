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

import com.viglet.turing.spring.jpa.TurUuid;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;


@Entity
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Table(name = "aem_source")
public class TurAemSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurUuid
    private String id;
    @Column
    private String url;
    @Column
    private String username;
    @Column
    private String password;
    @Column
    private String rootPath;
    @Column
    private String contentType;
    @Column
    private String group;
    @Column
    private String urlPrefix;
    @Column
    private String oncePattern;
    @Column
    private Locale locale;
    @Column
    private String localeClass;
    @Column
    private String deltaClass;
    @Column
    private String turingUrl;
    @Column
    private String turingApiKey;

    @Builder.Default
    @OneToMany(mappedBy = "turAemSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurAemSourceLocalePath> localePaths = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "turAemSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurAemAttributeMapping> attributeMappings = new HashSet<>();

    @Builder.Default
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "aem_sn_site", joinColumns = @JoinColumn(name = "source_id"))
    @Column(name = "sn_site", nullable = false)
    private Collection<String> turSNSites = new HashSet<>();


    public void setLocalePaths(Collection<TurAemSourceLocalePath> localePaths) {
        this.localePaths.clear();
        if (localePaths != null) {
            this.localePaths.addAll(localePaths);
        }
    }
}
