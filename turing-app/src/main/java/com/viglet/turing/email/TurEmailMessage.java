package com.viglet.turing.email;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TurEmailMessage {
    private final String senderEmail;
    private final String senderName;
    private final String recipientEmail;
    private final String recipientName;
    private final String subject;
    private final String htmlContent;
    private final String textContent;
}
