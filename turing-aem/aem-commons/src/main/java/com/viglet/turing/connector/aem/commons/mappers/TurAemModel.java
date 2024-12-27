package com.viglet.turing.connector.aem.commons.mappers;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TurAemModel {
    private String type;
    private String subType;
    private String className;
    private String validToIndex;
    @Builder.Default
    private List<TurAemTargetAttr> targetAttrs = new ArrayList<>();

    @Override
    public String toString() {
        return "TurAemModel{" +
                "type='" + type + '\'' +
                ", subType='" + subType + '\'' +
                ", className='" + className + '\'' +
                ", validToIndex='" + validToIndex + '\'' +
                ", targetAttrs=" + targetAttrs +
                '}';
    }
}
