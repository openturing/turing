package com.viglet.turing.connector.sprinklr;

import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrSearchResult;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrAccessToken;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.digester.plugins.PluginContext;

@Builder
@Getter
@Setter
public class TurSprinklrContext {
    private TurSprinklrSearchResult searchResult;
    private TurSprinklrAccessToken accessToken;
}
