package handlers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tersesystems.logback.classic.ILoggingEventFactory;
import com.tersesystems.logback.classic.LoggingEventFactory;
import com.tersesystems.logback.honeycomb.client.HoneycombClient;
import com.tersesystems.logback.honeycomb.client.HoneycombRequest;
import com.tersesystems.logback.honeycomb.client.HoneycombResponse;
import com.tersesystems.logback.tracing.SpanInfo;
import com.tersesystems.logback.tracing.SpanMarkerFactory;
import com.tersesystems.logback.uniqueid.IdGenerator;
import com.tersesystems.logback.uniqueid.RandomUUIDIdGenerator;
import com.typesafe.config.Config;
import logging.Attrs;
import logging.LogEntry;
import logging.RequestStartTime;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.marker.LogstashMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import play.api.UsefulException;
import play.libs.Json;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static handlers.HoneycombHandler.BatchingIterator.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Spliterator.ORDERED;

@Singleton
public class HoneycombHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final LoggingEventFactory loggingEventFactory = new LoggingEventFactory();

    private final HoneycombClient honeycombClient;
    private final LogstashEncoder encoder;

    private final Function<HoneycombRequest<ILoggingEvent>, byte[]> spanEncodeFunction;
    private final Function<HoneycombRequest<JsonNode>, byte[]> nodeEncodeFunction;
    private final Utils utils;

    @Inject
    public HoneycombHandler(Utils utils, HoneycombClient honeycombClient) {
        this.utils = utils;
        this.honeycombClient = honeycombClient;

        this.encoder = new LogstashEncoder();
        encoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        encoder.start();

        spanEncodeFunction = r -> encoder.encode(r.getEvent());
        nodeEncodeFunction = r -> Json.stringify(r.getEvent()).getBytes(UTF_8);
    }

    void handle(SpanInfo spanInfo, List<? extends LogEntry> rows, Http.RequestHeader request, UsefulException usefulException) {
        HoneycombRequest<ILoggingEvent> spanRequest = createSpanRequest(spanInfo, request, usefulException);
        CompletionStage<HoneycombResponse> f = honeycombClient.post(spanRequest, spanEncodeFunction);
        f.thenAccept(response -> {
            if (response != null && response.isSuccess()) {
                logger.debug("handle: Successful post of event = " + response.toString());
                postBackTraces(rows, spanInfo);
            } else {
                logger.error("handle: Bad honeycomb response {}", response != null ? response.toString() : null);
            }
        });
    }

    private void postBackTraces(List<? extends LogEntry> rows, SpanInfo spanInfo) {
        // https://docs.honeycomb.io/working-with-your-data/tracing/send-trace-data/#span-events
        Stream<HoneycombRequest<JsonNode>> stream = createBacktraceStream(rows.stream(), spanInfo);

        // Send batches to honeycomb 10 at a time
        batchedStreamOf(stream, 10).forEach(batch -> {
            CompletionStage<HoneycombResponse> f2 = honeycombClient.postBatch(batch, nodeEncodeFunction);
            f2.thenAccept(response -> {
                if (response != null && response.isSuccess()) {
                    logger.debug("postBackTraces: Successful post of backtraces = " + response.toString());
                } else {
                    logger.error("postBackTraces: Bad honeycomb response {}", response != null ? response.toString() : null);
                }
            });
        });
    }

    private Stream<HoneycombRequest<JsonNode>> createBacktraceStream(Stream<? extends LogEntry> rowStream, SpanInfo spanInfo) {
        return rowStream.map(row -> {
            JsonNode origNode = Json.parse(row.event());

            // Span events must be entered directly on the root, I think.
            ObjectNode node = Json.newObject();
            for (Iterator<Map.Entry<String, JsonNode>> it = origNode.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                node.put(entry.getKey(), entry.getValue().asText());
            }

            node.put("meta.span_type", "span_event");
            node.put("trace.parent_id", spanInfo.spanId());
            node.put("trace.trace_id", spanInfo.traceId());
            node.put("Timestamp", isoTime(row.timestamp()));
            node.put("name", origNode.get("message").textValue());

            return new HoneycombRequest<>(1, row.timestamp(), node);
        });
    }

    private HoneycombRequest<ILoggingEvent> createSpanRequest(SpanInfo spanInfo, Http.RequestHeader request, UsefulException usefulException) {
        Marker marker = utils.createMarker(spanInfo, request, 500);
        ILoggingEvent loggingEvent = loggingEventFactory.create(marker,
                (ch.qos.logback.classic.Logger) logger,
                Level.ERROR,
                usefulException.title,
                null,
                usefulException);
        return new HoneycombRequest<>(1, spanInfo.startTime(), loggingEvent);
    }

    private String isoTime(Instant eventTime) {
        return DateTimeFormatter.ISO_INSTANT.format(eventTime);
    }

    // https://stackoverflow.com/a/42531618/5266
    static class BatchingIterator<T> implements Iterator<List<T>> {
        /**
         * Given a stream, convert it to a stream of batches no greater than the
         * batchSize.
         *
         * @param originalStream to convert
         * @param batchSize      maximum size of a batch
         * @param <T>            type of items in the stream
         * @return a stream of batches taken sequentially from the original stream
         */
        public static <T> Stream<List<T>> batchedStreamOf(Stream<T> originalStream, int batchSize) {
            return asStream(new BatchingIterator<>(originalStream.iterator(), batchSize));
        }

        private static <T> Stream<T> asStream(Iterator<T> iterator) {
            return StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(iterator, ORDERED),
                    false);
        }

        private final int batchSize;
        private List<T> currentBatch;
        private final Iterator<T> sourceIterator;

        public BatchingIterator(Iterator<T> sourceIterator, int batchSize) {
            this.batchSize = batchSize;
            this.sourceIterator = sourceIterator;
        }

        @Override
        public boolean hasNext() {
            prepareNextBatch();
            return currentBatch != null && !currentBatch.isEmpty();
        }

        @Override
        public List<T> next() {
            return currentBatch;
        }

        private void prepareNextBatch() {
            currentBatch = new ArrayList<>(batchSize);
            while (sourceIterator.hasNext() && currentBatch.size() < batchSize) {
                currentBatch.add(sourceIterator.next());
            }
        }
    }
}
