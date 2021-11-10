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

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/import")
@Tag(name ="Import", description = "Import objects into Viglet Turing")
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
