package handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tersesystems.logback.honeycomb.client.HoneycombClient;
import com.tersesystems.logback.honeycomb.client.HoneycombRequest;
import com.tersesystems.logback.honeycomb.client.HoneycombResponse;
import com.tersesystems.logback.tracing.SpanInfo;
import logging.ID;
import logging.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static handlers.HoneycombHandler.BatchingIterator.batchedStreamOf;
import static java.util.Spliterator.ORDERED;

@Singleton
public class HoneycombHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HoneycombClient<JsonNode> honeycombClient;

    @SuppressWarnings("unchecked")
    @Inject
    public HoneycombHandler(HoneycombClient honeycombClient) {
        this.honeycombClient = (HoneycombClient<JsonNode>) honeycombClient;
    }

    void handle(SpanInfo spanInfo, Duration spanDuration, List<? extends LogEntry> rows, Http.RequestHeader request, UsefulException usefulException) {
        logger.info("handle: calling honeycomb for request {}", ID.get(request));

        try {
            HoneycombRequest<JsonNode> spanRequest = createSpanRequest(spanInfo, spanDuration, request, usefulException);
            final List<HoneycombRequest<JsonNode>> honeycombRequests = Arrays.asList(spanRequest);
            CompletionStage<List<HoneycombResponse>> f = honeycombClient.postBatch(honeycombRequests)
              .toCompletableFuture()
              .orTimeout(1, TimeUnit.SECONDS);
            f.whenComplete((responses, e) -> {
                if (responses != null) {
                    HoneycombResponse response = responses.get(0);
                    if (responses.get(0).isSuccess()) {
                        logger.info("handle: Successful post to honeycomb of event {}", response);
                        postBackTraces(rows, spanInfo);
                    } else {
                        logger.error("handle: Bad honeycomb response {}", response);
                    }
                } else {
                    logger.info("handle: responses = {}", responses);
                }
                if (e != null) {
                    logger.error("handle: Operation failed", e);
                }
            });
        } catch (Exception e) {
            logger.error("handle: Error posting to Honeycomb", e);
        }

    }

    // Create a fake span that covers the entire duration of the request / response
    private HoneycombRequest<JsonNode> createSpanRequest(SpanInfo spanInfo,
                                                         Duration spanDuration,
                                                         Http.RequestHeader request,
                                                         UsefulException usefulException) {
        // https://docs.honeycomb.io/working-with-your-data/tracing/send-trace-data/#manual-tracing
        ObjectNode node = Json.newObject();

        // Honeycomb required fields
        node.put("service_name", spanInfo.serviceName());
        node.set("trace.span_id", Json.toJson(spanInfo.spanId()));
        node.set("trace.parent_id", null);
        node.set("trace.trace_id", Json.toJson(spanInfo.traceId()));
        node.set("name", Json.toJson(request.toString()));
        node.set("duration_ms", Json.toJson(spanDuration.toMillis()));

        // Extra fields for more context
        node.set("request_id", Json.toJson(ID.get(request)));
        node.set("@timestamp", Json.toJson(spanInfo.startTime()));
        node.set("request.method", Json.toJson(request.method()));
        node.set("request.uri", Json.toJson(request.uri()));
        node.set("response.status_code", Json.toJson(500));
        node.set("exception", Json.toJson(usefulException.getMessage()));

        return new HoneycombRequest<>(1, spanInfo.startTime(), node);
    }

    // Create a bunch of sub spans under the trace from the logs that will act as backtracing...
    private Stream<HoneycombRequest<JsonNode>> createBacktraceStream(Stream<? extends LogEntry> rowStream, SpanInfo spanInfo) {
        return rowStream.map(row -> {
            JsonNode origNode = Json.parse(row.event());

            ObjectNode node = Json.newObject();
            for (Iterator<Map.Entry<String, JsonNode>> it = origNode.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                node.put(entry.getKey(), entry.getValue().asText());
            }

            node.put("service_name", spanInfo.serviceName());
            node.put("trace.parent_id", spanInfo.spanId());
            node.put("trace.trace_id", spanInfo.traceId());
            node.put("name", origNode.get("message").textValue());

            // Span events aren't reliable right now, only use 1ms spans :-)
            // https://docs.honeycomb.io/working-with-your-data/tracing/send-trace-data/#span-events
            if (isUsingSpanEvents()) {
                node.put("meta.span_type", "span_event");
            } else {
                node.put("trace.span_id", UUID.randomUUID().toString());
                node.put("duration_ms", 1);
            }

            return new HoneycombRequest<>(1, row.timestamp(), node);
        });
    }

    // Post the backtraces in batches of 10 per request.
    private void postBackTraces(List<? extends LogEntry> rows, SpanInfo spanInfo) {
        Stream<HoneycombRequest<JsonNode>> stream = createBacktraceStream(rows.stream(), spanInfo);

        // Send batches to honeycomb 1 at a time
        batchedStreamOf(stream, 1).forEach((List<HoneycombRequest<JsonNode>> batch) -> {
            CompletionStage<List<HoneycombResponse>> f2 = honeycombClient.postBatch(batch);
            f2.thenAccept(responses -> {
                for (HoneycombResponse response : responses) {
                    if (response.isSuccess()) {
                        logger.info("postBackTraces: Successful post of backtraces = " + response);
                    } else {
                        logger.error("postBackTraces: Bad honeycomb response {}", response);
                    }
                }
            });
        });
    }

    private boolean isUsingSpanEvents() {
        // should use a feature flag here :-)
        return false;
    }

    private String isoTime(Instant eventTime) {
        return DateTimeFormatter.ISO_INSTANT.format(eventTime);
    }

    // https://stackoverflow.com/a/42531618/5266
    public static class BatchingIterator<T> implements Iterator<List<T>> {
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
