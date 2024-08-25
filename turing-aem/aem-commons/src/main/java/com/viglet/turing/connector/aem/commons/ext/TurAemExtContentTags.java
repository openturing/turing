package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.bean.TurAemContentTag;
import com.viglet.turing.connector.aem.commons.bean.TurAemContentTags;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class TurAemExtContentTags implements TurAemExtAttributeInterface {
    public static final String TAGS_JSON_EXTENSION = "/jcr:content.tags.json";
    private static final TurMultiValue EMPTY = null;

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurAemExtContentTags");
        return new TurMultiValue(getTags(aemObject, turAemSourceContext).map(tags ->
                        tags.getTags().stream()
                                .map(TurAemContentTag::getTagID)
                                .toList())
                .orElse(EMPTY));
    }

    public static Optional<TurAemContentTags> getTags(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        String url = turAemSourceContext.getUrl() + aemObject.getPath() + TAGS_JSON_EXTENSION;
        return TurAemCommonsUtils.getResponseBody(url, turAemSourceContext, TurAemContentTags.class);
    }
}
