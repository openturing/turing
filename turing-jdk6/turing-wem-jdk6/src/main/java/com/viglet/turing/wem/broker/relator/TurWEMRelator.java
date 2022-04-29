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
package com.viglet.turing.wem.broker.relator;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.javabean.AttributedObject;
import com.vignette.logging.context.ContextLogger;

public class TurWEMRelator {
	private static final ContextLogger log = ContextLogger.getLogger(TurWEMRelator.class.getName());

	private TurWEMRelator() {
		throw new IllegalStateException("TurWEMRelator");
	}
	
	public static AttributedObject[] nestedRelators(List<String> relationTag, List<AttributedObject[]> currentRelation,
			int currentPosition) {
		List<AttributedObject> relators = new ArrayList<AttributedObject>();

		int nextPosition = currentPosition + 1;

		if (nextPosition < relationTag.size()) {
			return detectAttributesFromRelator(relationTag, currentRelation, currentPosition, nextPosition);
		} else {
			return generateRelatorAttributeArray(currentRelation, relators);
		}

	}

	private static AttributedObject[] generateRelatorAttributeArray(List<AttributedObject[]> currentRelation,
			List<AttributedObject> relators) {
		for (AttributedObject[] attributesFromRelation : currentRelation) {
			if (attributesFromRelation != null) {
				relators.addAll(Arrays.asList(attributesFromRelation));
			}
		}

		AttributedObject[] relatorsArr = new AttributedObject[relators.size()];
		relatorsArr = relators.toArray(relatorsArr);
		return relatorsArr;
	}

	private static AttributedObject[] detectAttributesFromRelator(List<String> relationTag,
			List<AttributedObject[]> currentRelation, int currentPosition, int nextPosition) {
		List<AttributedObject[]> nestedRelationChild = new ArrayList<AttributedObject[]>();
		for (AttributedObject[] attributesFromRelation : currentRelation) {

			for (AttributedObject attributeFromRelation : Arrays.asList(attributesFromRelation)) {
				try {
					AttributedObject[] childRelation = attributeFromRelation
							.getRelations(relationTag.get(nextPosition));

					nestedRelationChild.add(childRelation);

				} catch (ApplicationException e) {
					log.error(String.format("Error getting relations: %s of relation: %s",
							relationTag.get(currentPosition), relationTag.get(currentPosition - 1)), e);
				}
			}
		}
		return nestedRelators(relationTag, nestedRelationChild, nextPosition);
	}

}
