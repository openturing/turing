package com.viglet.turing.sprinklr.client.service.kb.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurSprinklrContent {
    private String contentType;
    private String contentSubType;
    private String title;
    private String markUpText;
}
