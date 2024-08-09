package com.viglet.turing.connector.sprinklr.commons.bean.kb;

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
