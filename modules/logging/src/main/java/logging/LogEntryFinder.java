package logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.db.Database;
import play.db.NamedDatabase;
import play.libs.Json;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        Instant ts = Instant.ofEpochMilli(rs.getLong("tse_ms"));
        int levelValue = rs.getInt("level_value");
        String level = rs.getString("level");
        String evt = rs.getString("evt");
        JsonNode json = Json.parse(evt);
        String message = json.path("message").asText("");
        String loggerName = json.path("logger_name").asText("");
        return new LogEntry(ts, levelValue, level, message, loggerName, evt);
    }

    private String getQueryStatement() {
        return config.getString("correlation.queryStatement");
    }

    private String byCorrelationIdStatement() {
        return config.getString("correlation.byCorrelationIdStatement");
    }

}
