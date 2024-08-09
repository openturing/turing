package com.viglet.turing.connector.sprinklr.commons.bean.kb;

import com.viglet.turing.connector.sprinklr.commons.bean.TurSprinklrGenericService;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurSprinklrKBSearch extends TurSprinklrGenericService {
    private TurSprinklrSearchData data;
}
