package logging;

import play.libs.typedmap.TypedKey;

import java.time.Instant;

final class Attrs {
    static final TypedKey<Instant> START_TIME = TypedKey.create("startTime");
}
