package handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tersesystems.logback.honeycomb.client.HoneycombClient;
import com.tersesystems.logback.honeycomb.client.HoneycombRequest;
import com.tersesystems.logback.honeycomb.client.HoneycombResponse;
import com.tersesystems.logback.tracing.SpanInfo;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import static handlers.BatchingIterator.batchedStreamOf;

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
        HoneycombRequest<JsonNode> spanRequest = createSpanRequest(spanInfo, spanDuration, request, usefulException);
        CompletionStage<HoneycombResponse> f = honeycombClient.post(spanRequest);
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
        Stream<HoneycombRequest<JsonNode>> stream = createBacktraceStream(rows.stream(), spanInfo);

        // Send batches to honeycomb 10 at a time
        batchedStreamOf(stream, 10).forEach((List<HoneycombRequest<JsonNode>> batch) -> {
            CompletionStage<List<HoneycombResponse>> f2 = honeycombClient.postBatch(batch);
            f2.thenAccept(responses -> {
                for (HoneycombResponse response : responses) {
                    if (response.isSuccess()) {
                        logger.debug("postBackTraces: Successful post of backtraces = " + response.toString());
                    } else {
                        logger.error("postBackTraces: Bad honeycomb response {}", response.toString());
                    }
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

            node.put("service_name", spanInfo.serviceName());
            node.put("trace.parent_id", spanInfo.spanId());
            node.put("trace.trace_id", spanInfo.traceId());
            node.put("Timestamp", isoTime(row.timestamp()));
            node.put("name", origNode.get("message").textValue());

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

    private HoneycombRequest<JsonNode> createSpanRequest(SpanInfo spanInfo,
                                                         Duration spanDuration,
                                                         Http.RequestHeader request,
                                                         UsefulException usefulException) {
        // https://docs.honeycomb.io/working-with-your-data/tracing/send-trace-data/#manual-tracing
        ObjectNode node = Json.newObject();

        node.put("service_name", spanInfo.serviceName());
        node.set("name", Json.toJson(request.toString()));
        node.set("correlation_id", Json.toJson(Long.toString(request.id())));
        node.set("trace.span_id", Json.toJson(spanInfo.spanId()));
        node.set("trace.parent_id", null);
        node.set("response.status_code", Json.toJson(500));
        node.set("exception", Json.toJson(usefulException.getMessage()));
        node.set("trace.trace_id", Json.toJson(spanInfo.traceId()));
        node.set("duration_ms", Json.toJson(spanDuration.toMillis()));

        return new HoneycombRequest<>(1, spanInfo.startTime(), node);
    }

    private boolean isUsingSpanEvents() {
        // should use a feature flag here :-)
        return false;
    }

    private String isoTime(Instant eventTime) {
        return DateTimeFormatter.ISO_INSTANT.format(eventTime);
    }


}
