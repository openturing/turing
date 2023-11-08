package com.viglet.turing.connector.aem.indexer.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TurAEMSiteTree {
    private List<TurAEMSiteTreeItem> items = new ArrayList<>();
    private boolean hasMore;
}
