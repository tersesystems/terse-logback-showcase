package logging;

import io.honeycomb.beeline.tracing.*;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.libhoney.HoneyClient;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class BeelineProvider implements Provider<Beeline> {
    private final Beeline beeline;

    @Inject
    public BeelineProvider(HoneyClient honeyClient) {
        SpanPostProcessor postProcessor = Tracing.createSpanProcessor(honeyClient, Sampling.alwaysSampler());
        SpanBuilderFactory factory      = Tracing.createSpanBuilderFactory(postProcessor, Sampling.alwaysSampler());
        Tracer tracer                   = Tracing.createTracer(factory);
        beeline                         = Tracing.createBeeline(tracer, factory);
    }

    @Override
    public Beeline get() {
        return beeline;
    }
}

