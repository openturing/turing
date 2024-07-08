/*
 * Copyright (C) 2016-2022 the original author or authors. 
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

package com.viglet.turing.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.commons.exception.TurException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
@Slf4j
@Component
public class TurUtils {
	private static File getTempDirectory() {
		return TurCommonsUtils.addSubDirToStoreDir("tmp");
	}

	public static File getFileFromMultipart(MultipartFile file) {
		File localFile = new File(
				randomTempFileOrDirectory());

		try {
			file.transferTo(localFile);
		} catch (IllegalStateException | IOException e) {
			log.error(e.getMessage(), e);
		}

		return localFile;
	}

	public static File extractZipFile(MultipartFile file) {

		File zipFile = getFileFromMultipart(file);

		File extractFolder = new File(
				randomTempFileOrDirectory());
		try {
			TurCommonsUtils.unZipIt(zipFile, new File(
					randomTempFileOrDirectory()));
			FileUtils.deleteQuietly(zipFile);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return extractFolder;
	}

	private static String randomTempFileOrDirectory() {
		return getTempDirectory().getAbsolutePath().concat(File.separator + "imp_" + UUID.randomUUID());
	}

	public static String asJsonString(final Object obj) throws TurException {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
			return mapper.writeValueAsString(obj);
		} catch (Exception e) {
			throw new TurException(e);
		}
    }
}
