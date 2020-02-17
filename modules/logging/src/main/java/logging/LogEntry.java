package logging;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public class LogEntry {
    private final Instant ts;
    private final int levelValue;
    private final String level;
    private final String message;
    private final String loggerName;
    private final String evt;

    public LogEntry(Instant ts, int levelValue, String level, String message, String loggerName, String evt) {
        this.ts = requireNonNull(ts, "Null instant");
        this.levelValue = levelValue;
        this.level = requireNonNull(level, "Null level");
        this.message = requireNonNull(message, "Null message");
        this.loggerName = requireNonNull(loggerName, "Null logger name");
        this.evt = requireNonNull(evt, "Null event");
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

    public String loggerName() {
        return loggerName;
    }

    public String event() {
        return evt;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "ts=" + ts +
                ", levelValue=" + levelValue +
                ", level='" + level + '\'' +
                ", message='" + message + '\'' +
                ", loggerName='" + loggerName + '\'' +
                ", evt='" + evt + '\'' +
                '}';
    }
}
