package com.viglet.turing.connector.cms.mappers;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TurCmsModel {
    private String type;
    private String subType;
    private String className;
    private String validToIndex;
    private List<TurCmsTargetAttr> targetAttrs;

    @Override
    public String toString() {
        return "TurCmsModel{" +
                "type='" + type + '\'' +
                ", subType='" + subType + '\'' +
                ", className='" + className + '\'' +
                ", validToIndex='" + validToIndex + '\'' +
                ", targetAttrs=" + targetAttrs +
                '}';
    }
}
