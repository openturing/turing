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

package com.viglet.turing.spring.utils;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Slf4j
public class TurSpringUtils {

    private TurSpringUtils() {
        throw new IllegalStateException("Utility class");
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
            TurCommonsUtils.unZipIt(zipFile, extractFolder);
            FileUtils.deleteQuietly(zipFile);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return extractFolder;
    }

    private static String randomTempFileOrDirectory() {
        return  TurCommonsUtils.getTempDirectory().getAbsolutePath().concat(File.separator + "imp_" + UUID.randomUUID());
    }
}
