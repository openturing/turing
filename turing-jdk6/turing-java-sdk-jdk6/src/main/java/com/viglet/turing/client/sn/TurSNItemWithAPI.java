package com.viglet.turing.client.sn;

import com.viglet.turing.client.sn.utils.TurSNClientUtils;
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

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.viglet.turing.client.sn.utils.TurSNClientUtils;
/**
 * Class with apiURL and method to return query parameters.
 *
 * @author Alexandre Oliveira
 *
 * @since 0.3.4
 */
public class TurSNItemWithAPI {

  static Logger logger = Logger.getLogger(TurSNItemWithAPI.class.getName());
  private String apiURL;

  public TurSNItemWithAPI() {
    super();
  }

  public String getApiURL() {
    return apiURL;
  }

  public void setApiURL(String apiURL) {
    this.apiURL = apiURL;
  }

  public TurSNQueryParamMap getQueryParams() {
    String apiURL = getApiURL();

    if (apiURL != null) {
      TurSNQueryParamMap queryParams = new TurSNQueryParamMap();
      try {
		for (Entry<String, List<String>> param : TurSNClientUtils.splitQuery(new URL(this.apiURL)).entrySet()) {
			queryParams.get(param.getKey()).addAll(param.getValue());
		}
      } catch (UnsupportedEncodingException e) {
        logger.log(Level.SEVERE, e.getMessage(), e);
      } catch (MalformedURLException e) {
        logger.log(Level.SEVERE, e.getMessage(), e);
      }
      return queryParams;
    }
    return null;
  }
}
