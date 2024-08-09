package com.viglet.turing.connector.sprinklr.commons.bean.folder;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TurSprinklrFolderData {
    private boolean hasMore;
    private List<TurSprinklrFolderResult> result;
    private int totalHits;
    private boolean selectAllSupported;
}
