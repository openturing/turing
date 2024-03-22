package com.viglet.turing.connector.webcrawler.ext;

import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.webcrawler.TurWCContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Optional;

@Slf4j
public class TurWCExtDate implements TurWCExtInterface {
    @Override
    public Optional<TurMultiValue> consume(TurWCContext context) {
        return Optional.of(TurMultiValue.singleItem(new Date()));
    }
}
