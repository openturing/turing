package com.viglet.turing.connector.aem.indexer;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class TurAemContext {
    private String url;
    private String username;
    private String password;
}
