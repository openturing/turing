package com.viglet.turing.connector.sprinklr.commons.ext;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.sprinklr.commons.TurSprinklrContext;

import java.util.Optional;

public class TurSprinklrExtText implements TurSprinklrExtInterface {

    @Override
    public Optional<TurMultiValue> consume(TurSprinklrContext context) {
        return Optional.of( TurMultiValue.singleItem(
                TurCommonsUtils.html2Text(context.getSearchResult().getContent().getMarkUpText())));
    }
}
