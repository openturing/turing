package com.viglet.turing.commons.utils;


import com.viglet.turing.commons.sn.search.TurSNParamType;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Predicate;

@Slf4j
public class TurHttpUtils {

    private TurHttpUtils() {}

    public static void removeParameterFromQueryByValue(List<NameValuePair> currentQuery, String value) {
        // value, se for uma faceta, está no formato "<facet>:<facet element>"
        Predicate<NameValuePair> exists = param -> param.getValue().equals(value);
        currentQuery.removeIf(exists);
    }

    public static URI removeParameterFromQueryByValue(URI uri, String value) {
        var newUri = new URIBuilder(uri);
        var query = newUri.getQueryParams();

        removeParameterFromQueryByValue(query, value);

        newUri.setParameters(query);

        try {
            return newUri.build();
        } catch (URISyntaxException e) {
            log.error("Exception while trying to remove parameter from URI", e);
        }
        return null;
    }

    public static void addParamOnQuery(List<NameValuePair> originalQuery, NameValuePair param) {
        originalQuery.add(param);
    }

    public static URI addParamOnQuery(URI uri, NameValuePair param) {
        var newUri = new URIBuilder(uri);
        newUri.addParameter(param.getName(), param.getValue());

        try {
            return newUri.build();
        } catch (URISyntaxException e) {
            log.error("Exception while trying to add parameter to URI", e);
        }
        return null;
    }

    /*
     * Espera receber uma faceta no formato "<facet>:<facet element>"
     */
    public static void addFacetFilterOnQuery(List<NameValuePair> currentQuery, String fqValue) {
        var newParam = new BasicNameValuePair(TurSNParamType.FILTER_QUERIES_DEFAULT, fqValue);
        addParamOnQuery(currentQuery, newParam);
    }

    /*
     * Espera receber uma faceta no formato "<facet>:<facet element>"
     */
    public static URI addFacetFilterOnQuery(URI uri, String fqValue) {
        var newParam = new BasicNameValuePair(TurSNParamType.FILTER_QUERIES_DEFAULT, fqValue);
        return addParamOnQuery(uri, newParam);
    }

    public static List<NameValuePair> getQueryParams(URI uri){
        return new URIBuilder(uri).getQueryParams();
    }
}
