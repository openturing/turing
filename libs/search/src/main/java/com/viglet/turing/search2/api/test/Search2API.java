package com.viglet.turing.search2.api.test;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;


@RestController
@RequestMapping("/api/searchtest")
@Api(value="Search Aplication", tags="Heartbeat", description="Heartbeat")
public class Search2API {

	
	@GetMapping
	public String searchInfo() {		

		return "Search Test";
	}
}