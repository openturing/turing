package com.viglet.turing.sprinklr.client.service.folder.request;

import lombok.*;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class TurSprinklrFolderRequestBody {
    private int start;
    private int rows;
}