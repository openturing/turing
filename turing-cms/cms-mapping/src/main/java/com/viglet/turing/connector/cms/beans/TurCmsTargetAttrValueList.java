package com.viglet.turing.connector.cms.beans;

import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;

import java.util.ArrayList;
import java.util.Optional;

public class TurCmsTargetAttrValueList extends ArrayList<TurCmsTargetAttrValue> {
    public <T> void addWithSingleValue(String attributeName, T value) {
        Optional.ofNullable(value).ifPresent(valuePresent ->
                this.add(new TurCmsTargetAttrValue(attributeName, TurMultiValue
                        .singleItem(valuePresent))));
    }

    public static TurCmsTargetAttrValueList singleItem(TurCmsTargetAttrValue turCmsTargetAttrValue) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        turCmsTargetAttrValueList.add(turCmsTargetAttrValue);
        return turCmsTargetAttrValueList;
    }

    public static <T> TurCmsTargetAttrValueList singleItem(String attributeName, T value) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        turCmsTargetAttrValueList.addWithSingleValue(attributeName, value);
        return turCmsTargetAttrValueList;
    }

    public static TurCmsTargetAttrValueList singleItem(String attributeName, TurMultiValue turMultiValue) {
        return TurCmsTargetAttrValueList.singleItem(new TurCmsTargetAttrValue(attributeName, turMultiValue));
    }

    public static TurCmsTargetAttrValueList singleItem(TurCmsTargetAttr turCmsTargetAttr) {
        return TurCmsTargetAttrValueList.singleItem(turCmsTargetAttr.getName(), turCmsTargetAttr.getTextValue());
    }
}
