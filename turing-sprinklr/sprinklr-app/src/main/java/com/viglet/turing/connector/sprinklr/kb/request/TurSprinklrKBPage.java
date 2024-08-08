package com.viglet.turing.connector.sprinklr.kb.request;

import lombok.*;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class TurSprinklrKBPage {
    private int page;
    private int size;
}
