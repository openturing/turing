package com.viglet.turing.connector.aem.indexer;

import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSource;
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
    private TurAemSource source;
}
