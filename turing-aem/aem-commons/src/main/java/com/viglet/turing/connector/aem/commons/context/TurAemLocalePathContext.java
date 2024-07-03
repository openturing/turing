package com.viglet.turing.connector.aem.commons.context;

import lombok.*;

import java.util.Locale;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class TurAemLocalePathContext {
    private Locale locale;
    private String path;
}
