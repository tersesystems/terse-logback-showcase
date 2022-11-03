package logging;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import play.libs.Json;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static logging.Constants.REQUEST_ID;

/**
 * A standalone component that queries a database and returns LogEntry.
 */
@Singleton
public class LogEntryFinder {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String url;
    private final LoggingExecutionContext executionContext;
    private final int pageSize;

    private final String queryStatement;
    private final String requestStatement;
    private final String idStatement;

    @Inject
    public LogEntryFinder(Config config,
                          LoggingExecutionContext executionContext) {
        this.url = config.getString("logging.url");
        this.executionContext = executionContext;
        this.pageSize = 20;
        this.queryStatement = config.getString("logging.sql.queryStatement");
        this.idStatement = config.getString("logging.sql.idStatement");
        this.requestStatement = config.getString("logging.sql.requestStatement");
    }

    Connection getConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(1000);
        Connection conn = config.createConnection(url);
        return conn;
    }

    public CompletionStage<List<LogEntry>> list(Integer offset) {
        // run the DB query in an IO execution context
        return supplyAsync(
                () -> {
                    try (Connection conn = getConnection()) {
                        return list(conn, pageSize, offset);
                    } catch (SQLException e) {
                        logger.error("Cannot query database", e);
                    }
                    return Collections.emptyList();
                },
                executionContext);
    }

    private List<LogEntry> list(Connection conn, int limit, int offset) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(queryStatement)) {
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

    public CompletionStage<List<LogEntry>> findByRequestId(String requestId) {
        return findByRequestId(requestId, pageSize);
    }

    public CompletionStage<List<LogEntry>> findByRequestId(String requestId, int limit) {
        // run the DB query in an IO execution context
        return supplyAsync(
                () -> {
                    try (Connection conn = getConnection()) {
                        return findByRequestId(conn, requestId, limit, 0);
                    } catch (SQLException e) {
                        logger.error("Cannot query database", e);
                    }
                    return Collections.emptyList();
                },
                executionContext);
    }

    public CompletionStage<Optional<LogEntry>> findById(String ts) {
        // run the DB query in an IO execution context
        return supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                return findById(conn, ts);
            } catch (SQLException e) {
                logger.error("Cannot query database", e);
            }
            return Optional.empty();
        },
        executionContext);
    }

    private Optional<LogEntry> findById(Connection conn, String eventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(idStatement)) {
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

    private List<LogEntry> findByRequestId(Connection conn, String correlationId, int limit, int offset) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(requestStatement)) {
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
        String requestId = json.path(REQUEST_ID).asText();
        String loggerName = json.path("logger_name").asText("");
        String uniqueId = rs.getString("event_id");
        return new LogEntry(ts, relativeNanos, level, requestId, message, loggerName, evt, uniqueId);
    }

}
