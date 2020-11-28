package logging;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.db.Database;
import play.db.NamedDatabase;
import play.inject.ApplicationLifecycle;
import play.libs.Json;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.supplyAsync;

@Singleton
public class LogEntryFinder {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Database db;
    private final Config config;
    private final LoggingExecutionContext executionContext;
    private final int pageSize;

    @Inject
    public LogEntryFinder(@NamedDatabase("logging") Database db,
                          Config config,
                          LoggingExecutionContext executionContext) {
        this.db = db;
        this.config = config;
        this.executionContext = executionContext;
        this.pageSize = 20;
    }

    public CompletionStage<List<LogEntry>> list(Integer offset) {
        return supplyAsync(
                () -> {
                    try (Connection conn = db.getConnection()) {
                        return list(conn, pageSize, offset);
                    } catch (SQLException e) {
                        logger.error("Cannot query database", e);
                    }
                    return Collections.emptyList();
                },
                executionContext);
    }

    private List<LogEntry> list(Connection conn, int limit, int offset) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(getQueryStatement())) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<LogEntry> list = new ArrayList<>();
                while (rs.next()) {
                    LogEntry logEntry = makeEntry(rs);
                    list.add(logEntry);
                }
                return list;
            }
        }
    }

    public CompletionStage<List<LogEntry>> findByCorrelation(String correlationId) {
        return findByCorrelation(correlationId, pageSize);
    }

    public CompletionStage<List<LogEntry>> findByCorrelation(String correlationId, int limit) {
        return supplyAsync(
                () -> {
                    try (Connection conn = db.getConnection()) {
                        return findByCorrelation(conn, correlationId, limit, 0);
                    } catch (SQLException e) {
                        logger.error("Cannot query database", e);
                    }
                    return Collections.emptyList();
                },
                executionContext);
    }

    public CompletionStage<Optional<LogEntry>> findById(String ts) {
        return supplyAsync(() -> {
            try (Connection conn = db.getConnection()) {
                return findById(conn, ts);
            } catch (SQLException e) {
                logger.error("Cannot query database", e);
            }
            return Optional.empty();
        },
        executionContext);
    }

    private Optional<LogEntry> findById(Connection conn, String eventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(byIdStatement())) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(makeEntry(rs));
                } else {
                    return Optional.empty();
                }
            }
        }
    }

    private List<LogEntry> findByCorrelation(Connection conn, String correlationId, int limit, int offset) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(byCorrelationIdStatement())) {
            ps.setString(1, correlationId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<LogEntry> list = new ArrayList<>();
                while (rs.next()) {
                    LogEntry logEntry = makeEntry(rs);
                    list.add(logEntry);
                }
                return list;
            }
        }
    }

    private LogEntry makeEntry(ResultSet rs) throws SQLException {
        final long seconds = rs.getLong("epoch_secs");
        final int nanos = rs.getInt("nanos");
        Instant ts = Instant.ofEpochSecond(seconds, nanos);
        Level logbackLevel = Level.toLevel(rs.getInt("level"));
        final org.slf4j.event.Level level = org.slf4j.event.Level.valueOf(logbackLevel.levelStr);
        String evt = rs.getString("content");
        JsonNode json = Json.parse(evt);
        Long relativeNanos = json.path("relative_ns").asLong();
        String message = json.path("message").asText("");
        String requestId = json.path("correlation_id").asText();
        String loggerName = json.path("logger_name").asText("");
        String uniqueId = rs.getString("event_id");
        return new LogEntry(ts, relativeNanos, level, requestId, message, loggerName, evt, uniqueId);
    }

    private String getQueryStatement() {
        return config.getString("logging.sql.queryStatement");
    }

    private String byIdStatement() {
        return config.getString("logging.sql.byIdStatement");
    }

    private String byCorrelationIdStatement() {
        return config.getString("logging.sql.byCorrelationIdStatement");
    }
}
