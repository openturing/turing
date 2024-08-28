package com.viglet.turing.connector.aem.sample.ext;

import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.TurAemExtContentInterface;
import com.viglet.turing.connector.aem.sample.beans.TurAemSampleModel;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurAemExtSampleModelJson implements TurAemExtContentInterface {
    public static final String FRAGMENT_PATH = "fragmentPath";
    public static final String MODEL_JSON_EXTENSION = ".model.json";

    @Override
    public TurCmsTargetAttrValueMap consume(TurAemObject aemObject,
                                            TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurAemExtSampleModelJson");
        String url = turAemSourceContext.getUrl() + aemObject.getPath() + MODEL_JSON_EXTENSION;
        TurCmsTargetAttrValueMap attrValues = new TurCmsTargetAttrValueMap();
        return TurAemCommonsUtils.getResponseBody(url, turAemSourceContext, TurAemSampleModel.class, false).map(model -> {
            getFragmentData(attrValues, model);
            return attrValues;
        }).orElseGet(TurCmsTargetAttrValueMap::new);
    }

    private static void getFragmentData(TurCmsTargetAttrValueMap attrValues, TurAemSampleModel model) {
        attrValues.addWithSingleValue(FRAGMENT_PATH, model.getFragmentPath(), true);
    }
}
