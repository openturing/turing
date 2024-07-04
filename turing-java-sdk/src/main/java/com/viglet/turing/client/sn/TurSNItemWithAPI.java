/*
 * Copyright (C) 2016-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.client.sn;

import lombok.Setter;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class with apiURL and method to return query parameters.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */
@Setter
public class TurSNItemWithAPI {
	static Logger logger = Logger.getLogger(TurSNItemWithAPI.class.getName());
	private String apiURL;

	public TurSNItemWithAPI() {
		super();
	}

	public Optional<String> getApiURL() {
		return Optional.ofNullable(apiURL);
	}

    public Optional<TurSNQueryParamMap> getQueryParams() {
		return getApiURL().map(api -> {
			TurSNQueryParamMap queryParams = new TurSNQueryParamMap();
			try {
				new URIBuilder(api).getQueryParams().forEach(param -> {
					if (queryParams.containsKey(param.getName()))
						queryParams.get(param.getName()).add(param.getValue());
					else
						queryParams.put(param.getName(), new ArrayList<>(Collections.singletonList(param.getValue())));
				});
			} catch (URISyntaxException e) {
				logger.log(Level.SEVERE, e.getMessage(), e);
			}
			return Optional.of(queryParams);
		}).orElse(Optional.empty());

	}

}
