package logging;

import java.time.Instant;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class LogEntry {
    private final Instant ts;
    private final int levelValue;
    private final String requestId;
    private final String level;
    private final String message;
    private final String loggerName;
    private final String evt;
    private final String uniqueId;

    public LogEntry(Instant ts, int levelValue, String requestId, String level, String message, String loggerName, String evt, String uniqueId) {
        this.ts = requireNonNull(ts, "Null instant");
        this.levelValue = levelValue;
        this.requestId = requestId;
        this.level = requireNonNull(level, "Null level");
        this.message = requireNonNull(message, "Null message");
        this.loggerName = requireNonNull(loggerName, "Null logger name");
        this.evt = requireNonNull(evt, "Null event");
        this.uniqueId = requireNonNull(uniqueId);
    }

    public Instant timestamp() {
        return ts;
    }

    public int levelValue() {
        return levelValue;
    }

    public String level() {
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
                "ts=" + ts +
                ", levelValue=" + levelValue +
                ", requestId='" + requestId + '\'' +
                ", level='" + level + '\'' +
                ", message='" + message + '\'' +
                ", loggerName='" + loggerName + '\'' +
                ", evt='" + evt + '\'' +
                ", uniqueId='" + uniqueId + '\'' +
                '}';
    }
}
