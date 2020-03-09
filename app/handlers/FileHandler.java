package handlers;

import logging.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FileHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void handle(String correlationId, List<LogEntry> rows) {
        try {
            Path cwd = FileSystems.getDefault().getPath("").toAbsolutePath();
            Path logs = Files.createDirectories(cwd.resolve("logs"));
            Path path = logs.resolve("backtrace_" + correlationId + ".log");
            try (BufferedWriter writer = Files.newBufferedWriter(path, UTF_8)) {
                for (LogEntry row : rows) {
                    writer.write(row.event());
                    writer.newLine();
                }
            } catch (IOException e) {
                logger.error("Cannot write file!", e);
            }
        } catch (IOException e) {
            logger.error("Cannot create directories!", e);
        }
    }
}
