package com.viglet.turing.sprinklr.client.service.folder.response;

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
