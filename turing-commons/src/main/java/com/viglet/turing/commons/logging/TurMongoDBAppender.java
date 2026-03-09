package com.viglet.turing.commons.logging;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import ch.qos.logback.classic.pattern.Abbreviator;
import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;

@Slf4j
@Setter
public class TurMongoDBAppender extends TurMongoDBAppenderBase {

    public static final int MAX_LENGTH_PACKAGE_NAME = 40;
    private static final Abbreviator ABBREVIATOR = new TargetLengthBasedClassNameAbbreviator(1);
    private static String cachedHostName;

    // Move Mapper to a static constant to avoid re-initializing it every log line
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .addModule(new JodaModule())
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, true)
            .build();

    // Use an Executor to handle DB writes asynchronously
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    static {
        try {
            cachedHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            cachedHostName = "unknown";
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!enabled || collection == null) {
            return;
        }

        // 1. Capture the log data immediately while still on the calling thread
        TurLoggingGeneral logEntry = TurLoggingGeneral.builder()
                .level(event.getLevel().toString())
                .logger(abbreviatePackage(event.getLoggerName()))
                .message(event.getFormattedMessage())
                .date(new Date(event.getTimeStamp()))
                .stackTrace(getStackTrace(event))
                .clusterNode(cachedHostName)
                .build();

        // 2. Offload the expensive JSON parsing and DB IO to the executor
        executor.submit(() -> {
            try {
                String json = MAPPER.writeValueAsString(logEntry);
                collection.insertOne(Document.parse(json));
            } catch (Exception e) {
                log.info("Failed to log to MongoDB: " + e.getMessage());
            }
        });
    }

    @Override
    public void stop() {
        executor.shutdown();
        super.stop();
    }

    private static @NotNull String getStackTrace(ILoggingEvent event) {
        StringBuilder stackStraceBuilder = new StringBuilder();
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            String throwableStr = ThrowableProxyUtil.asString(throwableProxy);
            stackStraceBuilder.append(throwableStr);
            stackStraceBuilder.append(CoreConstants.LINE_SEPARATOR);
        }
        return stackStraceBuilder.toString();
    }

    private static @NotNull String abbreviatePackage(String packageName) {
        if (packageName == null)
            return "";
        if (packageName.length() <= MAX_LENGTH_PACKAGE_NAME)
            return packageName;
        return ABBREVIATOR.abbreviate(packageName);
    }

}