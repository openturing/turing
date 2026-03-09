/*
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.turing.spring;

import java.io.IOException;

import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Filter to handle trailing slashes in API URLs
 * Removes trailing slash from request URI internally without redirect
 */
@Component
public class TurTrailingSlashFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();

        // Remove trailing slash for API calls (except root paths)
        if (requestURI.startsWith("/api/") && requestURI.length() > 1 && requestURI.endsWith("/")) {
            String newURI = requestURI.substring(0, requestURI.length() - 1);

            // Wrap request to return URI without trailing slash
            HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(httpRequest) {
                @Override
                public String getRequestURI() {
                    return newURI;
                }

                @Override
                public StringBuffer getRequestURL() {
                    StringBuffer url = new StringBuffer(getScheme())
                            .append("://")
                            .append(getServerName());
                    if ((getScheme().equals("http") && getServerPort() != 80) ||
                            (getScheme().equals("https") && getServerPort() != 443)) {
                        url.append(':').append(getServerPort());
                    }
                    url.append(newURI);
                    return url;
                }
            };

            chain.doFilter(wrapper, response);
        } else {
            chain.doFilter(request, response);
        }
    }
}
