package com.viglet.turing.connector.sprinklr.service.token;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrSource;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrToken;
import com.viglet.turing.connector.sprinklr.persistence.repository.TurSprinklrTokenRepository;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class TurSprinklrTokenService {
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String GRANT_TYPE = "grant_type";
    private static final String CLIENT_CREDENTIALS = "client_credentials";
    private final TurSprinklrTokenRepository turSprinklrTokenRepository;
    private static final String SPRINKLR_TOKEN_SERVICE = "https://api2.sprinklr.com/prod2/oauth/token";
    private static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String POST = "POST";
    private static final String CONTENT_TYPE = "Content-Type";

    @Inject
    public TurSprinklrTokenService(TurSprinklrTokenRepository turSprinklrTokenRepository) {
        this.turSprinklrTokenRepository = turSprinklrTokenRepository;
    }

    public TurSprinklrToken getAccessToken(TurSprinklrSource turSprinklrSource) {
        return turSprinklrTokenRepository.findById(turSprinklrSource.getApiKey())
                .filter(token -> token.getExpirationDate().after(new Date()))
                .orElseGet(() -> generateAccessToken(turSprinklrSource));
    }

    private TurSprinklrToken generateAccessToken(TurSprinklrSource turSprinklrSource) {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        RequestBody formBody = new FormBody.Builder()
                .add(CLIENT_ID, turSprinklrSource.getApiKey())
                .add(CLIENT_SECRET, turSprinklrSource.getSecretKey())
                .add(GRANT_TYPE, CLIENT_CREDENTIALS)
                .build();
        Request request = new Request.Builder()
                .url(SPRINKLR_TOKEN_SERVICE)
                .method(POST, formBody)
                .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return Optional.ofNullable(response.body()).map(responseBody -> {
                try {
                    TurSprinklrToken turSprinklrToken = new ObjectMapper()
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            .readValue(response.body().string(), TurSprinklrToken.class);
                    turSprinklrToken.setApiKey(turSprinklrSource.getApiKey());
                    turSprinklrToken.setExpirationDate(getExprirationDate(turSprinklrToken));
                    System.out.println(turSprinklrToken);
                    turSprinklrTokenRepository.findById(turSprinklrSource.getApiKey()).ifPresentOrElse(token ->
                                    updateToken(token, turSprinklrToken),
                            () -> turSprinklrTokenRepository.save(turSprinklrToken));
                    return turSprinklrToken;
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                    return null;
                }
            }).orElse(null);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private static Date getExprirationDate(TurSprinklrToken turSprinklrToken) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.SECOND, turSprinklrToken.getExpiresIn());
        return cal.getTime();
    }

    private void updateToken(TurSprinklrToken token, TurSprinklrToken turSprinklrToken) {
        token.setAccessToken(turSprinklrToken.getAccessToken());
        token.setTokenType(turSprinklrToken.getTokenType());
        token.setRefreshToken(turSprinklrToken.getRefreshToken());
        token.setExpiresIn(turSprinklrToken.getExpiresIn());
        token.setExpirationDate(turSprinklrToken.getExpirationDate());
        turSprinklrTokenRepository.save(token);
    }
}
