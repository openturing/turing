package com.viglet.turing.api.ocr;

import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.filesystem.commons.TurFileUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/ocr")
@Tag(name = "OCR", description = "OCR API")
public class TurOcrAPI {

    @PostMapping
    public TurFileAttributes convertToText(@RequestParam("file") MultipartFile multipartFile) {
       return TurFileUtils.documentToText(multipartFile);
    }
}
