/*
 * Copyright (C) 2016-2018 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as publitured by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You turould have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.spring.security;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

public class TurCsrfHeaderFilter extends OncePerRequestFilter {
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrf != null) {
			Cookie cookie = WebUtils.getCookie(request, "XSRF-TOKEN");
			String token = csrf.getToken();
			if (cookie == null || token != null && !token.equals(cookie.getValue())) {
				cookie = new Cookie("XSRF-TOKEN", token);
				cookie.setSecure(false);
				cookie.setPath("/");
				response.addCookie(cookie);
			}
		}
		filterChain.doFilter(request, response);
	}
}