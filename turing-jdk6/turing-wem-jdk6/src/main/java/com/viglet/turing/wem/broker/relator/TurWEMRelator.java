/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
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
