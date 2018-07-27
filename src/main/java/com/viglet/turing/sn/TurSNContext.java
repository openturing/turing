package com.viglet.turing.sn;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class TurSNContext {
	@RequestMapping("/sn/{siteName}")
	String sn(@PathVariable("siteName") String siteName) {
		return "sn/templates/index";
	}
}
