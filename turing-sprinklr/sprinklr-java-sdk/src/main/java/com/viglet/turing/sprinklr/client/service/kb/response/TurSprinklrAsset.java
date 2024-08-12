package com.viglet.turing.sprinklr.client.service.kb.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurSprinklrAsset {
    private String assetId;
    private String assetType;
    private String assetCategory;
    private boolean resolvable;
}
