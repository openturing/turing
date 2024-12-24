package com.viglet.turing.connector.sprinklr.commons;

import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrPluginContext;
import com.viglet.turing.connector.sprinklr.commons.kb.response.TurSprinklrSearchResult;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Builder
@Getter
@Setter
@ToString
public class TurSprinklrContext {
    private TurSprinklrSearchResult searchResult;
    private TurSprinklrPluginContext pluginContext;
}
