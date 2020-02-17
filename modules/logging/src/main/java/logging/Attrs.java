package logging;

import play.libs.typedmap.TypedKey;

import java.time.Instant;

public final class Attrs {
    public static final TypedKey<Instant> START_TIME = TypedKey.create("startTime");
}
