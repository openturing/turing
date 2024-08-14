package com.viglet.turing.connector.sprinklr.export.bean;

import lombok.*;

import java.util.Collection;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TurSprinklrExchange {
    private Collection<TurSprinklrSourceExchange> sources;
}
