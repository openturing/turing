/*
 * Copyright (C) 2016-2019 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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
package com.viglet.turing.wem.mappers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.beans.TuringTagMap;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.logging.context.ContextLogger;

public class CTDMappings {
	private TuringTagMap turingTagMap;
	private String classValidToIndex = null;
	private static final ContextLogger log = ContextLogger.getLogger(CTDMappings.class);

	// OLD getIndexAttrTag
	public List<TuringTag> getTuringTagBySrcAttr(String srcAttrName) {

		List<TuringTag> turingTags = new ArrayList<TuringTag>();
		if (log.isDebugEnabled())
			log.debug("CTDMappings attribute: " + srcAttrName);

		if (turingTagMap != null) {
			for (TuringTag turingTag : TuringUtils.turingTagMapToSet(turingTagMap)) {
				if (turingTag != null && turingTag.getSrcXmlName().equals(srcAttrName))
					turingTags.add(turingTag);

			}
		}
		return turingTags;
	}

	// Get TagList
	public Set<String> getTagList() {
		Set<String> tagNames = new HashSet<String>();

		for (TuringTag turingTag : TuringUtils.turingTagMapToSet(turingTagMap)) {
			tagNames.add(turingTag.getTagName());
		}		

		if (log.isDebugEnabled()) {
			log.debug("getIndexAttrs Tag Names");
			for (String tagName : tagNames)
				log.debug(tagName);
		}

		return tagNames;
	}

	public CTDMappings(TuringTagMap turingTagMap) {
		this.turingTagMap = turingTagMap;
	}

	public TuringTagMap getTuringTagMap() {
		return turingTagMap;
	}

	public String getClassValidToIndex() {
		return classValidToIndex;
	}

	public void setClassValidToIndex(String classValidToIndex) {
		this.classValidToIndex = classValidToIndex;
	}

}