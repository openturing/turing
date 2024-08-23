package com.viglet.turing.connector.cms.beans;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.HashMap;
import java.util.Optional;

@Slf4j
public class TurCmsTargetAttrValueMap extends HashMap<String, TurMultiValue> {
    public <T> void addWithSingleValue(String attributeName, T value, boolean override) {
        Optional.ofNullable(value).ifPresent(v -> {
            if (!this.containsKey(attributeName) || (containsKey(attributeName) && override)) {
                switch (v) {
                    case TurMultiValue turMultiValue -> this.put(attributeName, turMultiValue);
                    case Date date -> this.put(attributeName, TurMultiValue.singleItem(date, override));
                    case Boolean bool -> this.put(attributeName,TurMultiValue.singleItem(bool, override));
                    case String string -> this.put(attributeName,TurMultiValue.singleItem(string, override));
                    default -> this.put(attributeName,TurMultiValue.singleItem(v.toString(), override));
                }
            } else {
                switch (v) {
                    case TurMultiValue turMultiValue -> this.get(attributeName).addAll(turMultiValue);
                    case Date date -> this.get(attributeName).addAll(TurMultiValue.singleItem(date, override));
                    case Boolean bool -> this.get(attributeName).addAll(TurMultiValue.singleItem(bool, override));
                    case String string -> this.get(attributeName).addAll(TurMultiValue.singleItem(string, override));
                    default -> this.get(attributeName).addAll(TurMultiValue.singleItem(v.toString(), override));
                }
            }
        });
    }

    public void addWithSingleValue(TurCmsTargetAttrValue targetAttrValue) {
        Optional.ofNullable(targetAttrValue).ifPresent(target -> {
            if (this.containsKey(target.getTargetAttrName())) {
                this.get(target.getTargetAttrName()).addAll(target.getMultiValue());
            } else {
                this.put(target.getTargetAttrName(), target.getMultiValue());
            }
        });
    }

    public void merge(TurCmsTargetAttrValueMap fromMap) {
        fromMap.keySet().forEach(fromKey -> {
            if (this.containsKey(fromKey)) {
                if (fromMap.get(fromKey).isOverride()) {
                    this.put(fromKey, fromMap.get(fromKey));
                }
                else {
                    this.get(fromKey).addAll(fromMap.get(fromKey));
                }
            }
            else {
                this.put(fromKey, fromMap.get(fromKey));
            }
        });
    }

    public static TurCmsTargetAttrValueMap singleItem(TurCmsTargetAttrValue turCmsTargetAttrValue) {
        TurCmsTargetAttrValueMap turCmsTargetAttrValueMap = new TurCmsTargetAttrValueMap();
        turCmsTargetAttrValueMap.put(turCmsTargetAttrValue.getTargetAttrName(), turCmsTargetAttrValue.getMultiValue());
        return turCmsTargetAttrValueMap;
    }

    public static <T> TurCmsTargetAttrValueMap singleItem(String attributeName, T value, boolean override) {
        TurCmsTargetAttrValueMap turCmsTargetAttrValueMap = new TurCmsTargetAttrValueMap();
        turCmsTargetAttrValueMap.addWithSingleValue(attributeName, value, override);
        return turCmsTargetAttrValueMap;
    }

    public static TurCmsTargetAttrValueMap singleItem(String attributeName, TurMultiValue turMultiValue) {
        return TurCmsTargetAttrValueMap.singleItem(new TurCmsTargetAttrValue(attributeName, turMultiValue));
    }


    public static TurCmsTargetAttrValueMap singleItem(TurCmsTargetAttr turCmsTargetAttr, boolean override) {
        return TurCmsTargetAttrValueMap.singleItem(turCmsTargetAttr.getName(), turCmsTargetAttr.getTextValue(),
                override);
    }
}
