package com.viglet.turing.connector.sprinklr.commons.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TurSprinklrSearchData {
    private List<TurSprinklrSearchResult> searchResults;
}
