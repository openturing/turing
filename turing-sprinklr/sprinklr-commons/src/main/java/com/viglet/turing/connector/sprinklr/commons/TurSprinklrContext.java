package com.viglet.turing.connector.sprinklr.commons;

import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrSearchResult;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrAccessToken;
import com.viglet.turing.sprinklr.plugins.PluginContext;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// Classe repetida
@Builder
@Getter
@Setter
@ToString
public class TurSprinklrContext {
    private TurSprinklrSearchResult searchResult;
    private TurSprinklrAccessToken accessToken;
    private PluginContext pluginContext;
}
