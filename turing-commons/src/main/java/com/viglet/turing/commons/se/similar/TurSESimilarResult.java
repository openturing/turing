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

package com.viglet.turing.commons.se.similar;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * More Like This feature of Solr.
 * 
 * @since 0.3.4
 */

@Builder
@Setter
@Getter
public class TurSESimilarResult {

	private String id;
	private String title;
	private String type;
	private String url;

}
