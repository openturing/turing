package com.viglet.turing.connector.sprinklr.commons.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TurSprinklrMapping {
    private String mappedProjectId;
    private String CommunityMessageId;
    private List<String> mappedCategoryIds;
    private List<String> mappedTopicIds;
    private String communityPermalink;
}
