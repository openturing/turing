package com.viglet.turing.email.provider;

import com.viglet.turing.email.TurEmailMessage;

public interface TurEmailProvider {
    String getProviderType();

    void sendEmail(TurEmailMessage message, String apiKey);
}
