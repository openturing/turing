package com.viglet.turing.sprinklr.client.service.kb.response;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurSprinklrTranslation {
    private Date updateTime;
    private String translationStatus;
    private boolean newContentAvailableForTranslation;
}
