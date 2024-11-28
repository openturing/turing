package com.viglet.turing.client.auth;

import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import java.net.URL;
import lombok.Getter;

@Getter
public class TurServer {

  private static final String PROVIDER_NAME_DEFAULT = "turing-java-sdk";

  private final URL serverUrl;

  private final String apiKey;

  private final String providerName;

  public TurServer(URL serverUrl, TurApiKeyCredentials apiKeyCredentials) {
    super();
    this.serverUrl = serverUrl;
    this.apiKey = apiKeyCredentials.getApiKey();
    this.providerName = PROVIDER_NAME_DEFAULT;
  }
}
