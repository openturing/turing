package com.viglet.turing.sprinklr.client.service.folder.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TurSprinklrFolderMapping {
private String mappedProjectId;
private List<String> mappedCategoryIds;
private List<String> mappedTopicIds;
}
