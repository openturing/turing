package com.viglet.turing.sprinklr.client.service.token;

import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TurSprinklrSecretKey {
    private String apiKey;
    private String secretKey;
    private String environment;
}
