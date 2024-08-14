package com.viglet.turing.sprinklr.client.service.token;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
public class TurSprinklrAccessToken implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String apiKey;
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("refresh_token")
    private String refreshToken;
    @JsonProperty("token_type")
    private String tokenType;
    @JsonProperty("expires_in")
    private int expiresIn;
    private Date expirationDate;
    private String environment;
}
