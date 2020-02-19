package filters;

import play.mvc.*;

import javax.inject.Singleton;
import java.time.Clock;
import java.time.Instant;

@Singleton
public class StartTimeFilter extends EssentialFilter {

    private final Clock clock;

    public StartTimeFilter() {
        this(Clock.systemUTC());
    }

    public StartTimeFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public EssentialAction apply(EssentialAction next) {
        return EssentialAction.of(request -> next.apply(request.addAttr(Attrs.START_TIME, Instant.now(clock))));
    }
}
