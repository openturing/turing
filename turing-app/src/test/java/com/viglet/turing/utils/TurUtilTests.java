package com.viglet.turing.utils;

import org.jetbrains.annotations.NotNull;

public class TurUtilTests {
    @NotNull
    public static String getUrlTemplate(String serviceUrl, String id) {
        return serviceUrl.concat("/").concat(id);
    }
}
