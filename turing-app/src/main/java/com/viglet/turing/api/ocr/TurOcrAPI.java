/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.api.ocr;

import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.filesystem.commons.TurFileUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.MalformedURLException;
import java.net.URI;

/**
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
@Slf4j
@RestController
@RequestMapping("/api/ocr")
@Tag(name = "OCR", description = "OCR API")
public class TurOcrAPI {

    @PostMapping("/file")
    public TurFileAttributes fileToText(@RequestParam("file") MultipartFile multipartFile) {
       return TurFileUtils.documentToText(multipartFile);
    }

    @PostMapping("/url")
    public TurFileAttributes urlToText(@RequestBody TurOcrFromUrl turOcrFromUrl) {
        try {
            return TurFileUtils.urlContentToText(URI.create(turOcrFromUrl.getUrl()).toURL());
        }
        catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
        return new TurFileAttributes();
    }
}
