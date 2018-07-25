package com.viglet.turing.swagger;

import springfox.documentation.spring.web.paths.AbstractPathProvider;

public class TurCustomPathPrivider extends AbstractPathProvider {

	@Override
	protected String applicationPath() {
		return "/";
	}

	@Override
	protected String getDocumentationPath() {
		return "/api";
	}

}
