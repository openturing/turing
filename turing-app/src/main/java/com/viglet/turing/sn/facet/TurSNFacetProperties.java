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

import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
@Builder
@Getter
@Setter
public class TurSNFacetProperties {
    private TurSNSiteFacetFieldEnum facetType;
    private TurSNSiteFacetFieldEnum facetItemType;
    private List<String> facetItems;
}