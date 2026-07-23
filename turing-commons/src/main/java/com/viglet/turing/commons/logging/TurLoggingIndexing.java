package com.viglet.turing.commons.logging;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.viglet.core.logging.VigletIsoDateSerializer;
import com.viglet.turing.commons.indexing.TurIndexingStatus;
import com.viglet.turing.commons.indexing.TurLoggingStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.annotation.JsonSerialize;

@Slf4j
@Builder
@Getter
@Setter
@ToString
public class TurLoggingIndexing implements Serializable {
    private TurIndexingStatus status;
    private String source;
    private String contentId;
    private String url;
    private List<String> sites;
    private String environment;
    private Locale locale;
    private String transactionId;
    private String checksum;
    private TurLoggingStatus resultStatus;
    private String details;
    @JsonSerialize(using = VigletIsoDateSerializer.class)
    private Date date;

}
