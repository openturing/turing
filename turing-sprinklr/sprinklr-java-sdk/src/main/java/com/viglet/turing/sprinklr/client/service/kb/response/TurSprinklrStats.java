package com.viglet.turing.sprinklr.client.service.kb.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurSprinklrStats {
    private int recommendCount;
    private int usageCount;
    private int ratingCount;
    private int ratingAvg;
    private int agentViewCount;
    private int livechatViewCount;
    private int helpfulCount;
    private int notHelpCount;
    private int communityHelpfulCount;
    private int communityNotHelpfulCount;
    private int livechatHelpfulCount;
    private int livechatNotHelpfulCount;
}
