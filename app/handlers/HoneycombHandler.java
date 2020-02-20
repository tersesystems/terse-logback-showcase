package handlers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tersesystems.logback.classic.LoggingEventFactory;
import com.tersesystems.logback.honeycomb.client.HoneycombClient;
import com.tersesystems.logback.honeycomb.client.HoneycombRequest;
import com.tersesystems.logback.tracing.SpanInfo;
import com.tersesystems.logback.tracing.SpanMarkerFactory;
import com.tersesystems.logback.uniqueid.RandomUUIDIdGenerator;
import com.typesafe.config.Config;
import filters.Attrs;
import logging.LogEntry;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.marker.LogstashMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.UsefulException;
import play.libs.Json;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Spliterator.ORDERED;

@Singleton
public class HoneycombHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HoneycombClient honeycombClient;
    private final String dataSet;
    private final String writeKey;
    private final LogstashEncoder encoder;
    private final RandomUUIDIdGenerator idgen;
    private final String serviceName;

    @Inject
    public HoneycombHandler(Config config, HoneycombClient honeycombClient) {
        this.honeycombClient = honeycombClient;
        this.writeKey = config.getString("honeycomb.writeKey");
        this.dataSet = config.getString("honeycomb.dataSet");
        this.serviceName = config.getString("honeycomb.serviceName");

        this.idgen = new RandomUUIDIdGenerator();
        this.encoder = new LogstashEncoder();
        encoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        encoder.start();
    }

    void recordHoneycombEvent(List<LogEntry> rows, Http.RequestHeader request, UsefulException usefulException) {
        SpanInfo spanInfo = createRootSpan(request);
        HoneycombRequest<ILoggingEvent> spanRequest = createSpanRequest(spanInfo, request, usefulException);
        honeycombClient.postEvent(writeKey, dataSet, spanRequest, this::spanEncodeFunction).thenAccept(response -> {
            if (response != null) {
                System.out.println(response.toString());
            }

            // https://docs.honeycomb.io/working-with-your-data/tracing/send-trace-data/#span-events
            Stream<HoneycombRequest<JsonNode>> requestsStream = createEventRequests(rows.stream(), spanInfo);
            BatchingIterator.batchedStreamOf(requestsStream, 10).forEach(batch ->
                honeycombClient.postBatch(writeKey, dataSet, batch, this::nodeEncodeFunction)
            );
        });

    }

    private Stream<HoneycombRequest<JsonNode>> createEventRequests(Stream<LogEntry> rowStream, SpanInfo spanInfo) {
        return rowStream.map(row -> {
            ObjectNode node = (ObjectNode) Json.parse(row.event());
            node.put("meta.span_type", "span_event");
            node.put("trace.parent_id", spanInfo.spanId());
            node.put("trace.trace_id", spanInfo.traceId());
            node.put("name", row.event());
            return new HoneycombRequest<>(1, row.timestamp(), node);
        });
    }

    private byte[] nodeEncodeFunction(HoneycombRequest<JsonNode> request) {
        String jsonString = Json.stringify(request.getEvent());
        return jsonString.getBytes(UTF_8);
    }

    private SpanInfo createRootSpan(Http.RequestHeader request) {
        SpanInfo.Builder spanBuilder = SpanInfo.builder();
        spanBuilder.setRootSpan(idgen::generateId, "rootSpan");
        spanBuilder.setServiceName(serviceName);
        Optional<Instant> startTime = request.attrs().getOptional(Attrs.START_TIME);
        if (startTime.isPresent()) {
            Instant st = startTime.get();
            spanBuilder.setStartTime(st);
            spanBuilder.setDurationSupplier(() -> Duration.between(st, Instant.now()));
        } else {
            spanBuilder.startNow();
        }

        return spanBuilder.build();
    }

    private byte[] spanEncodeFunction(HoneycombRequest<ILoggingEvent> request) {
        return encoder.encode(request.getEvent());
    }

    private HoneycombRequest<ILoggingEvent> createSpanRequest(SpanInfo spanInfo, Http.RequestHeader request, UsefulException usefulException) {
        LoggingEventFactory loggingEventFactory = new LoggingEventFactory();
        LogstashMarker marker = new SpanMarkerFactory().create(spanInfo);
        ILoggingEvent loggingEvent = loggingEventFactory.create(marker,
                (ch.qos.logback.classic.Logger) logger, Level.ERROR, usefulException.title, null, usefulException);
        return new HoneycombRequest<>(1, spanInfo.startTime(), loggingEvent);
    }

    // https://stackoverflow.com/a/42531618/5266
    static class BatchingIterator<T> implements Iterator<List<T>> {
        /**
         * Given a stream, convert it to a stream of batches no greater than the
         * batchSize.
         * @param originalStream to convert
         * @param batchSize maximum size of a batch
         * @param <T> type of items in the stream
         * @return a stream of batches taken sequentially from the original stream
         */
        public static <T> Stream<List<T>> batchedStreamOf(Stream<T> originalStream, int batchSize) {
            return asStream(new BatchingIterator<>(originalStream.iterator(), batchSize));
        }

        private static <T> Stream<T> asStream(Iterator<T> iterator) {
            return StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(iterator,ORDERED),
                    false);
        }

        private int batchSize;
        private List<T> currentBatch;
        private Iterator<T> sourceIterator;

        public BatchingIterator(Iterator<T> sourceIterator, int batchSize) {
            this.batchSize = batchSize;
            this.sourceIterator = sourceIterator;
        }

        @Override
        public boolean hasNext() {
            prepareNextBatch();
            return currentBatch!=null && !currentBatch.isEmpty();
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
