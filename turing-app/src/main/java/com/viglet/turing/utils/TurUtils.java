/*
 * Copyright (C) 2016-2022 the original author or authors.
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

package com.viglet.turing.utils;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.spring.security.auth.ITurAuthenticationFacade;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.UUID;

@Component
public class TurUtils {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	@Autowired
	private ITurAuthenticationFacade authenticationFacade;

	private static File getTempDirectory() {
		return TurCommonsUtils.addSubDirToStoreDir("tmp");
	}

	public static File getFileFromMultipart(MultipartFile file) {
		File localFile = new File(
				randomTempFileOrDirectory());

		try {
			file.transferTo(localFile);
		} catch (IllegalStateException | IOException e) {
			logger.error(e.getMessage(), e);
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
			logger.error(e.getMessage(), e);
		}

		return extractFolder;
	}

	private static String randomTempFileOrDirectory() {
		return getTempDirectory().getAbsolutePath().concat(File.separator + "imp_" + UUID.randomUUID());
	}

	public String getCurrentUsername() {
		Authentication authentication = authenticationFacade.getAuthentication();
		return authentication.getName();
	}
}
