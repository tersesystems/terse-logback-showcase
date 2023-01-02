package handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.tersesystems.logback.classic.NanoTimeMarker;
import com.tersesystems.logback.tracing.SpanInfo;
import com.tersesystems.logback.tracing.SpanMarkerFactory;
import com.tersesystems.logback.uniqueid.IdGenerator;
import com.tersesystems.logback.uniqueid.RandomUUIDIdGenerator;
import com.typesafe.config.Config;
import logging.LogEntry;
import logging.RequestStartTime;
import net.logstash.logback.marker.LogstashMarker;
import net.logstash.logback.marker.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import play.libs.Json;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Singleton
public class Utils {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final IdGenerator idgen = new RandomUUIDIdGenerator();
    private final SpanMarkerFactory spanMarkerFactory = new SpanMarkerFactory();
    private final String serviceName;
    private final String dataSet;
    private final String team;
    private final boolean honeycombEnabled;
    private final boolean sentryEnabled;
    private final boolean filesEnabled;

    @Inject
    public Utils(Config config) {
        this.serviceName = config.getString("honeycomb.serviceName");
        this.dataSet = config.getString("honeycomb.dataSet");
        this.team = config.getString("honeycomb.team");
        this.honeycombEnabled = config.getBoolean("honeycomb.enabled");
        this.sentryEnabled = config.getBoolean("sentry.enabled");
        this.filesEnabled = config.getBoolean("files.enabled");

        logger.info("honeycomb.enabled = {}, sentry.enabled = {}, files.enabled = {}",
          honeycombEnabled, sentryEnabled, filesEnabled);
        if (honeycombEnabled) {
            logger.info("honeycomb.serviceName = {}", serviceName);
            logger.info("honeycomb.dataSet = {}", dataSet);
        }
    }

    public String generateHoneycombLink(LogEntry entry) {
        if (! isHoneycombEnabled()) {
            return null;
        }

        JsonNode json = Json.parse(entry.event());
        String traceId = json.findPath("trace.trace_id").asText();
        if (traceId.equals("")) {
            return null;
        }

        long startTSE = entry.timestamp().toEpochMilli() / 1000;
        long endTSE = entry.timestamp().plusSeconds(60).toEpochMilli() / 1000;
        return views.html.log.honeycomb.render(team, dataSet, traceId, startTSE, endTSE).body();
    }

    boolean isHoneycombEnabled() {
        return this.honeycombEnabled;
    }
    boolean isSentryEnabled() {
        return this.sentryEnabled;
    }

    public Marker createMarker(SpanInfo spanInfo, Http.RequestHeader request, Integer responseStatus) {
        LogstashMarker spanMarker = spanMarkerFactory.create(spanInfo);
        LogstashMarker methodMarker = Markers.append("request.method", request.method());
        LogstashMarker uriMarker = Markers.append("request.uri", request.uri());
        LogstashMarker statusMarker = Markers.append("response.status", responseStatus);
        return spanMarker.and(methodMarker).and(uriMarker).and(statusMarker);
    }

    public SpanInfo createRootSpan(Http.RequestHeader request) {
        SpanInfo.Builder spanBuilder = SpanInfo.builder();
        spanBuilder.setRootSpan(idgen::generateId, request.toString());
        spanBuilder.setServiceName(serviceName);
        Instant st = RequestStartTime.get(request);
        spanBuilder.setStartTime(st);
        spanBuilder.setDurationSupplier(() -> Duration.between(st, Instant.now()));

        return spanBuilder.build();
    }

    public boolean isFilesEnabled() {
        return this.filesEnabled;
    }
}
