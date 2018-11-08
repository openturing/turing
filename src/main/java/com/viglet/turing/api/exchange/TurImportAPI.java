package com.viglet.turing.api.exchange;

import java.io.IOException;

import org.apache.commons.compress.archivers.ArchiveException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.exchange.TurImportExchange;

import io.swagger.annotations.Api;

@RestController
@RequestMapping("/api/import")
@Api(tags = "Import", description = "Import objects into Viglet Turing")
public class TurImportAPI {

	@Autowired
	private TurImportExchange turImportExchange;

	@PostMapping
	@Transactional
	public TurExchange shImport(@RequestParam("file") MultipartFile multipartFile)
			throws IllegalStateException, IOException, ArchiveException {
		return turImportExchange.importFromMultipartFile(multipartFile);
	}

}
