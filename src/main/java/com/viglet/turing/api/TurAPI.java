package com.viglet.turing.api;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;


@RestController

@RequestMapping("/api")
@Api(value="/", tags="Heartbeat", description="Heartbeat")
public class TurAPI {

	@Autowired
	TurAPIBean turAPIBean;

	//@PreAuthorize("#oauth2.hasScope('read')")
	@GetMapping
	public TurAPIBean info() throws JSONException {

		turAPIBean.setProduct("Viglet Turing");

		return turAPIBean;
	}
}