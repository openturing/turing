package com.viglet.turing.connector.sprinklr.bean;

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
