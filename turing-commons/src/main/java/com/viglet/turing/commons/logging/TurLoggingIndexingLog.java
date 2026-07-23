package com.viglet.turing.commons.logging;

import lombok.extern.slf4j.Slf4j;

/**
 * Emits indexing-status events to the {@code TurLoggingIndexingLog} logger, which
 * the persistent logback appenders (Mongo/Redis) consume to store each
 * {@link TurLoggingIndexing} document.
 *
 * <p>The event is only emitted when a persistent logging engine is active
 * ({@code turing.logging.engine} is {@code mongodb} or {@code redis}). With the
 * default {@code none} the status is dropped, so console/plain-text appenders no
 * longer print useless {@code TurLoggingIndexing@hash} lines. The engine value is
 * wired at startup by turing-app via {@link #setEngine(String)}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public class TurLoggingIndexingLog {
   private static final String ENGINE_MONGODB = "mongodb";
   private static final String ENGINE_REDIS = "redis";

   /** Selected {@code turing.logging.engine}: {@code none | mongodb | redis}. */
   private static volatile String engine = "none";

   private TurLoggingIndexingLog() {
      throw new IllegalStateException("Log Ingestion Utility");
   }

   /**
    * Sets the active logging engine. Called once at startup from turing-app.
    *
    * @param loggingEngine the {@code turing.logging.engine} value; {@code null}
    *                      is treated as {@code none}
    */
   public static void setEngine(String loggingEngine) {
      engine = loggingEngine == null ? "none" : loggingEngine;
   }

   public static void setStatus(TurLoggingIndexing status) {
      if (isPersistentEngine()) {
         log.info("{}", status);
      }
   }

   private static boolean isPersistentEngine() {
      return ENGINE_MONGODB.equalsIgnoreCase(engine) || ENGINE_REDIS.equalsIgnoreCase(engine);
   }
}
