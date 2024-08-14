package com.viglet.turing.sprinklr.client.service.kb.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TurSprinklrSearchData {
    private List<TurSprinklrSearchResult> searchResults;
}
