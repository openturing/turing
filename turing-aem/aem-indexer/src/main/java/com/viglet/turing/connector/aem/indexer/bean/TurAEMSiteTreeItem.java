package com.viglet.turing.connector.aem.indexer.bean;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TurAEMSiteTreeItem {
    private String title;
    private String path;
    private String icon;
    private boolean selectable;
    private boolean hasItems;
}
