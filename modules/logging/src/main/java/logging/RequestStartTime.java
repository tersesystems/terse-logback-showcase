package logging;

import play.mvc.Http;

import java.time.Instant;
import java.util.Optional;

/**
 * Public API for getting the start time out of the request.
 */
public class RequestStartTime {

    public static Instant get(Http.RequestHeader request) {
        return request.attrs().get(Attrs.START_TIME);
    }


}
