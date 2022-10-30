package logging;

import play.api.http.HttpConfiguration;
import play.api.libs.typedmap.TypedMap;
import play.api.mvc.*;
import play.api.mvc.request.DefaultRequestFactory;
import play.api.mvc.request.RemoteConnection;
import play.api.mvc.request.RequestTarget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

/**
 * A request factory that records the start time of the request from
 * creation.  This helps with honeycomb tracing.
 */
@Singleton
public class StartTimeRequestFactory extends DefaultRequestFactory {

    @Inject
    public StartTimeRequestFactory(CookieHeaderEncoding cookieHeaderEncoding, SessionCookieBaker sessionBaker, FlashCookieBaker flashBaker) {
        super(cookieHeaderEncoding, sessionBaker, flashBaker);
    }

    public StartTimeRequestFactory(HttpConfiguration config) {
        super(config);
    }

    @Override
    public RequestHeader createRequestHeader(RemoteConnection connection,
                                             String method,
                                             RequestTarget target,
                                             String version,
                                             Headers headers,
                                             TypedMap attrs) {
        return super.createRequestHeader(connection, method, target, version, headers, attrs)
                .addAttr(Attrs.START_TIME.asScala(), Instant.now());
    }
}
