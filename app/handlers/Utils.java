package handlers;

import com.tersesystems.logback.classic.StartTimeMarker;
import com.tersesystems.logback.tracing.SpanInfo;
import com.tersesystems.logback.tracing.SpanMarkerFactory;
import com.tersesystems.logback.uniqueid.IdGenerator;
import com.tersesystems.logback.uniqueid.RandomUUIDIdGenerator;
import com.typesafe.config.Config;
import logging.RequestStartTime;
import net.logstash.logback.marker.LogstashMarker;
import net.logstash.logback.marker.Markers;
import org.slf4j.Marker;
import play.mvc.Http;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class Utils {

    private final IdGenerator idgen = new RandomUUIDIdGenerator();
    private final SpanMarkerFactory spanMarkerFactory = new SpanMarkerFactory();
    private final String serviceName;

    @Inject
    public Utils(Config config) {
        this.serviceName = config.getString("honeycomb.serviceName");
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
        Optional<Instant> startTime = RequestStartTime.get(request);
        if (startTime.isPresent()) {
            Instant st = startTime.get();
            spanBuilder.setStartTime(st);
            spanBuilder.setDurationSupplier(() -> Duration.between(st, Instant.now()));
        } else {
            //logger.warn("createRootSpan: request {} doesn't have startTime attribute!", request);
            spanBuilder.startNow();
        }

        return spanBuilder.build();
    }

}
