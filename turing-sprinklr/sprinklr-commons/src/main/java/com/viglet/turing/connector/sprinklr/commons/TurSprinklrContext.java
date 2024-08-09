package com.viglet.turing.connector.sprinklr.commons;

import com.viglet.turing.connector.sprinklr.commons.bean.kb.TurSprinklrSearchResult;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class TurSprinklrContext {
    private TurSprinklrSearchResult searchResult;

}
