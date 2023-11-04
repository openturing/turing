package com.viglet.turing.utils;

import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import org.jetbrains.annotations.NotNull;

public class TurUtilTests {
    @NotNull
    public static String getUrlTemplate(String serviceUrl, String id) {
        return serviceUrl.concat("/").concat(id);
    }
}
