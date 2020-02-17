package logging;

import java.time.Instant;

public class LogEntry {
    private final Instant ts;
    private final int levelValue;
    private final String level;
    private final String evt;

    public LogEntry(Instant ts, int levelValue, String level, String evt) {
        this.ts = ts;
        this.levelValue = levelValue;
        this.level = level;
        this.evt = evt;
    }

    public int levelValue() {
        return levelValue;
    }

    public String level() {
        return level;
    }

    public String event() {
        return evt;
    }

    public Instant timestamp() {
        return ts;
    }

    @Override
    public String toString() {
        return "LoggingRow{" +
                "ts=" + ts +
                ", levelValue=" + levelValue +
                ", level='" + level + '\'' +
                ", evt='" + evt + '\'' +
                '}';
    }
}
