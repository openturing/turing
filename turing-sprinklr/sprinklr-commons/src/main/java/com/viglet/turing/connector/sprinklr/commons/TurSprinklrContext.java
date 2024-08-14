package com.viglet.turing.connector.sprinklr.commons;

import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrSearchResult;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrAccessToken;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class TurSprinklrContext {
    private TurSprinklrSearchResult searchResult;
    private TurSprinklrAccessToken accessToken;

}
