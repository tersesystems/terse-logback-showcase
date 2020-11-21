package logging;

import org.slf4j.event.Level;

import java.time.Instant;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class LogEntry {
    private final Instant timestamp;
    private final Long relativeNanos;
    private final Level level;
    private final String requestId;
    private final String message;
    private final String loggerName;
    private final String evt;
    private final String uniqueId;

    public LogEntry(Instant timestamp, Long relativeNanos, Level level, String requestId, String message, String loggerName, String evt, String uniqueId) {
        this.timestamp = requireNonNull(timestamp, "Null instant");
        this.relativeNanos = relativeNanos;
        this.level = level;
        this.requestId = requestId;
        this.message = requireNonNull(message, "Null message");
        this.loggerName = requireNonNull(loggerName, "Null logger name");
        this.evt = requireNonNull(evt, "Null event");
        this.uniqueId = requireNonNull(uniqueId);
    }

    public Instant timestamp() {
        return timestamp;
    }

    public Long relativeNanos() { return relativeNanos; }

    public Level level() {
        return level;
    }

    public String message() {
        return message;
    }

    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }

    public String loggerName() {
        return loggerName;
    }

    public String event() {
        return evt;
    }

    public String uniqueId() {
        return uniqueId;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "timestamp=" + timestamp +
                ", relativeNanos=" + relativeNanos +
                ", level=" + level +
                ", requestId='" + requestId + '\'' +
                ", message='" + message + '\'' +
                ", loggerName='" + loggerName + '\'' +
                ", evt='" + evt + '\'' +
                ", uniqueId='" + uniqueId + '\'' +
                '}';
    }
}
