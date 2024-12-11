package com.viglet.turing.connector.sprinklr.ext;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.sprinklr.commons.TurSprinklrContext;

import java.util.List;
import java.util.Optional;

public class TurSprinklrExtTag implements TurSprinklrExtInterface {

    @Override
    public Optional<TurMultiValue> consume(TurSprinklrContext context) {
       List<String> tags = context.getSearchResult().getTags();
       if (!tags.isEmpty()) {
         return Optional.of(new TurMultiValue(tags));
       }
       else {
           return Optional.empty();
       }
    }
}
