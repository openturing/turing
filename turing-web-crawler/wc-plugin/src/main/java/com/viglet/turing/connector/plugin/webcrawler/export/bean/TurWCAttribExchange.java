package com.viglet.turing.connector.plugin.webcrawler.export.bean;

import lombok.*;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TurWCAttribExchange {
    private String name;
    private String className;
    private String text;
}
