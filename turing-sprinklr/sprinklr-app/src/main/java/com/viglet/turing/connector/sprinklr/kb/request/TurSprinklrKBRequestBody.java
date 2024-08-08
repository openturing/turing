package com.viglet.turing.connector.sprinklr.kb.request;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class TurSprinklrKBRequestBody {
    @Builder.Default
    private List<TurSprinklrKBFilter> filters = new ArrayList<>();
    private TurSprinklrKBPage page;
}
