package logging;

import play.mvc.Http;

import java.time.Instant;
import java.util.Optional;

public class RequestStartTime {

    public static Optional<Instant> get(Http.RequestHeader request) {
        return request.attrs().getOptional(Attrs.START_TIME);
    }


}
