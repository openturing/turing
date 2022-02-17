package com.viglet.turing.api.test

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController


@RestController
class HtmlController {

	@GetMapping("/api/v2/kotlin")
	fun helloKotlin(): String {
		return "hello world"
	}

}