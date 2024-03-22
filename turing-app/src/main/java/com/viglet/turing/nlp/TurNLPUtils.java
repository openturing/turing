package com.viglet.turing.nlp;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.pdfcleanup.PdfCleaner;
import com.itextpdf.pdfcleanup.autosweep.CompositeCleanupStrategy;
import com.itextpdf.pdfcleanup.autosweep.RegexBasedCleanupStrategy;
import com.viglet.turing.nlp.output.blazon.RedactionCommand;
import com.viglet.turing.nlp.output.blazon.RedactionScript;
import com.viglet.turing.nlp.output.blazon.SearchString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TurNLPUtils {

    public static final String REDACT_PDF = "redact.pdf";
    public static final String PDF_SIGNATURE = "%PDF-";

    public RedactionScript createRedactionScript(TurNLPResponse turNLPResponse) {
       return Optional.ofNullable(turNLPResponse)
                .map(TurNLPResponse::getEntityMapWithProcessedValues)
                .map(this::createRedactionScript).orElse(new RedactionScript());
    }
    public RedactionScript createRedactionScript(Map<String, List<String>> entityMap) {
        List<RedactionCommand> redactionCommands = new ArrayList<>();
        RedactionScript redactionScript = new RedactionScript();
        redactionScript.setVersion("1");
        entityMap.forEach((key, value) ->
                        Optional.ofNullable(value).ifPresent(v ->
                                v.forEach(term -> {
                                    RedactionCommand redactionCommand = new RedactionCommand();
                                    SearchString searchString = new SearchString();
                                    searchString.setMatchWholeWord(true);
                                    searchString.setString(term);
                                    redactionCommand.setSearchString(searchString);
                                    redactionCommands.add(redactionCommand);
                                })));
        redactionScript.setRedactionCommands(redactionCommands);
        return redactionScript;
    }
    public void redactPdf(File file, List<String> terms) {
        if (isPDF(file)) {
            PdfReader pdfReader = null;
            try {
                pdfReader = new PdfReader(file);
                pdfReader.setUnethicalReading(true);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            if (pdfReader != null) {
                try (PdfDocument pdf = new PdfDocument(pdfReader,
                        new PdfWriter(file.getAbsolutePath().concat(REDACT_PDF)))) {
                    CompositeCleanupStrategy strategy = new CompositeCleanupStrategy();

                    strategy.add(new RegexBasedCleanupStrategy(redactRegex(terms))
                            .setRedactionColor(ColorConstants.DARK_GRAY));
                    try {
                        PdfCleaner.autoSweepCleanUp(pdf, strategy);
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    public boolean isPDF(File file) {
        try (Scanner input = new Scanner(new FileReader(file))) {
            while (input.hasNextLine()) {
                if (input.nextLine().contains(PDF_SIGNATURE)) {
                    return true;
                }
            }
        } catch (FileNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    private Pattern redactRegex(List<String> terms) {

        StringBuffer stringBuffer = new StringBuffer();
        terms.forEach(term -> stringBuffer.append(term.concat("|")));
        if (!stringBuffer.isEmpty()) {
            stringBuffer.deleteCharAt(stringBuffer.length() - 1);
            String pattern = "\\b(".concat(stringBuffer.toString().replace(")", "")
                    .replace("(", "")).concat(")\\b");
            return Pattern.compile(Pattern.quote(pattern), Pattern.CASE_INSENSITIVE);
        } else {
            return Pattern.compile("", Pattern.CASE_INSENSITIVE);
        }
    }
}
