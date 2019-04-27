/*
 * Copyright (C) 2016-2019 the original author or authors. 
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

package com.viglet.turing.api;

import org.jasypt.intf.service.JasyptStatelessService;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;

@RestController
@RequestMapping("/api")
@Api(value="/", tags="Heartbeat", description="Heartbeat")
public class TurAPI {
	@Value("${encryptedv3.property}")
	private String encrypt;
	 @Autowired
	    ApplicationContext appCtx;
	
	 final JasyptStatelessService service = new JasyptStatelessService();
	 
	@Autowired
	TurAPIBean turAPIBean;

	//@PreAuthorize("#oauth2.hasScope('read')")
	@GetMapping
	public TurAPIBean info() throws JSONException {

		turAPIBean.setProduct("Viglet Turing");

		return turAPIBean;
	}
	
	@GetMapping("/test")
	public String test() {
		Environment environment = appCtx.getBean(Environment.class);
		   System.out.println("turJasypt: " + environment.getProperty("encryptedv3.property"));
		   
		   return environment.getProperty("encryptedv3.property");

	}

}