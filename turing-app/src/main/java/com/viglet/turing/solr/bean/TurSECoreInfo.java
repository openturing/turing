package com.viglet.turing.solr.bean;

import java.util.List;

public record TurSECoreInfo(String name, long numDocs, List<String> usedBySites) {
}
