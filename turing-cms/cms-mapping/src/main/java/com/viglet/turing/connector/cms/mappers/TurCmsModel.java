package com.viglet.turing.connector.cms.mappers;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TurCmsModel {
    private String type;
    private String subType;
    private String className;
    private String validToIndex;
    @Builder.Default
    private List<TurCmsTargetAttr> targetAttrs = new ArrayList<>();

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
