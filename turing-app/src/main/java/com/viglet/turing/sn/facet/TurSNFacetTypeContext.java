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

package com.viglet.turing.sn.facet;

import com.viglet.turing.commons.se.TurSEFilterQueryParameters;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
@AllArgsConstructor
@Getter
@Setter
public class TurSNFacetTypeContext {
    private TurSNSiteFieldExtDto turSNSiteFacetFieldExtDto;
    private TurSNSite turSNSite;
    private TurSEFilterQueryParameters queryParameters;


    public TurSNFacetTypeContext(TurSNSite turSNSite, TurSEFilterQueryParameters queryParameters) {
        this.turSNSiteFacetFieldExtDto = null;
        this.turSNSite = turSNSite;
        this.queryParameters = queryParameters;
    }
    public boolean isSpecificField() {
        return turSNSiteFacetFieldExtDto != null;
    }
}
