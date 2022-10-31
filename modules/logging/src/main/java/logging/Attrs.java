package logging;

import play.libs.typedmap.TypedKey;

import java.time.Instant;

import static logging.Constants.REQUEST_ID;

final class Attrs {
    static final TypedKey<Instant> START_TIME = TypedKey.create("startTime");
    static final TypedKey<String> ID = TypedKey.create(REQUEST_ID);
}
