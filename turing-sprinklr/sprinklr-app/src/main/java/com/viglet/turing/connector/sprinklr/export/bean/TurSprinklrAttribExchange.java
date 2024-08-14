package com.viglet.turing.connector.sprinklr.export.bean;

import lombok.*;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TurSprinklrAttribExchange {
    private String name;
    private String className;
    private String text;
}
