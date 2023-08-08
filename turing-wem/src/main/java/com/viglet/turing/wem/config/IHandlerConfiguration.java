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
package com.viglet.turing.wem.config;

import java.util.List;

import com.vignette.as.client.common.AsLocaleData;

public interface IHandlerConfiguration {

	String getTuringURL();
	
	String getProviderName();
	
	TurSNSiteConfig getDefaultSNSiteConfig();

	TurSNSiteConfig getSNSiteConfig(String site);

	TurSNSiteConfig getSNSiteConfig(String site, String locale);

	TurSNSiteConfig getSNSiteConfig(String site, AsLocaleData asLocaleData);

	String getMappingsXML();

	List<String> getSitesAssocPriority();

	String getCDAContextName();

	String getCDAURLPrefix();

	String getCDAURLPrefix(String site);

	String getCDAContextName(String site);

	String getLogin();

	String getPassword();

	String getFileSourcePath();

}
