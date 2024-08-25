package com.viglet.turing.connector.aem.commons.ext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.aem.commons.bean.TurAemContentTag;
import com.viglet.turing.connector.aem.commons.bean.TurAemContentTags;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class TurAemExtContentTags implements TurAemExtAttributeInterface {
    public static final String TAGS_JSON_EXTENSION = "/jcr:content.tags.json";
    private static final TurMultiValue EMPTY = null;

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurAemExtContentTags");
        return getTags(aemObject, turAemSourceContext).map(tags -> {
            List<String> list = new ArrayList<>();
            for (TurAemContentTag turAemContentTag : tags.getTags()) {
                String tagID = turAemContentTag.getTagID();
                list.add(tagID);
            }
            return new TurMultiValue(list);
        }).orElseGet(() -> new TurMultiValue(EMPTY));
    }

    public static Optional<TurAemContentTags> getTags(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        String url = turAemSourceContext.getUrl() + aemObject.getPath() + TAGS_JSON_EXTENSION;
        return TurAemCommonsUtils.getResponseBody(url, turAemSourceContext).map(json -> {
            ObjectMapper objectMapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            if (TurCommonsUtils.isJSONValid(json)) {
                try {
                    return objectMapper.readValue(json, TurAemContentTags.class);
                } catch (JsonProcessingException e) {
                    log.error(e.getMessage(), e);
                }
            }
            return null;
        });
    }
}
