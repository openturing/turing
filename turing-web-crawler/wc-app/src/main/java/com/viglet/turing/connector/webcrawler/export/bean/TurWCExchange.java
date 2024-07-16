package com.viglet.turing.connector.webcrawler.export.bean;

import lombok.*;

import java.util.Collection;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TurWCExchange {
    private Collection<TurWCSourceExchange> sources;
}
