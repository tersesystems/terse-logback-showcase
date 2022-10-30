import com.fasterxml.jackson.databind.JsonNode;
import com.tersesystems.logback.honeycomb.client.HoneycombClient;
import com.tersesystems.logback.honeycomb.client.HoneycombRequest;
import com.tersesystems.logback.honeycomb.okhttp.HoneycombOkHTTPClientService;
import com.typesafe.config.Config;
import play.Environment;
import play.inject.Binding;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class Module extends play.inject.Module {
  public List<Binding<?>> bindings(Environment environment, Config config) {
    return Collections.singletonList(bindClass(HoneycombClient.class).toProvider(HoneycombProvider.class));
  }

  static class HoneycombProvider implements Provider<HoneycombClient> {

    private final HoneycombClient<JsonNode> client;

    @Inject
    public HoneycombProvider(Config config) {

      String writeKey = config.getString("honeycomb.writeKey");
      String dataSet = config.getString("honeycomb.dataSet");
      Function<HoneycombRequest<JsonNode>, byte[]> defaultEncodeFunction = r -> null; // don't use this
      final HoneycombOkHTTPClientService service = new HoneycombOkHTTPClientService();
      this.client = service.newClient(writeKey, dataSet, defaultEncodeFunction);
    }

    @Override
    public HoneycombClient get() {
      return client;
    }
  }
}
