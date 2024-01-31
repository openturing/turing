package com.viglet.turing.connector.webcrawler.ext;

import com.viglet.turing.connector.webcrawler.TurWCContext;
import com.viglet.turing.connector.webcrawler.ext.TurWCExtInterface;
import generator.RandomUserAgentGenerator;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

@Slf4j
public class TurWCExtDate implements TurWCExtInterface {

    public static final String SOLR_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    @Override
    public Optional<String> consume(TurWCContext context) {
        return setSolrDate(new Date());
    }

    private static Optional<String> setSolrDate(Date date) {
        SimpleDateFormat solrDateFormat = new SimpleDateFormat(SOLR_DATE_PATTERN, Locale.US);
         return Optional.of(solrDateFormat.format(date));

    }
}
