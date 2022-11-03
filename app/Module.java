import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tersesystems.logback.honeycomb.client.HoneycombClient;
import com.tersesystems.logback.honeycomb.client.HoneycombRequest;
import com.tersesystems.logback.honeycomb.okhttp.HoneycombOkHTTPClient;
import com.typesafe.config.Config;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import play.Environment;
import play.inject.Binding;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

// In Play, any root defined Module is loaded automatically.
public class Module extends play.inject.Module {
  public List<Binding<?>> bindings(Environment environment, Config config) {
    return Collections.singletonList(bindClass(HoneycombClient.class).toProvider(HoneycombProvider.class));
  }

  static class HoneycombProvider implements Provider<HoneycombClient> {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger("okhttp3.logging.HttpLoggingInterceptor");

    private final HoneycombClient<JsonNode> client;

    @Inject
    public HoneycombProvider(Config config) {
      ObjectMapper om = new ObjectMapper().findAndRegisterModules();
      final ObjectWriter writer = om.writer();

      String writeKey = config.getString("honeycomb.writeKey");
      String dataSet = config.getString("honeycomb.dataSet");
      Function<HoneycombRequest<JsonNode>, byte[]> defaultEncodeFunction = r -> {
        try {
          // https://docs.honeycomb.io/api/events/#anatomy-of-an-event
          ObjectNode node = om.createObjectNode();
          JsonNode time = om.readTree(r.getTimestamp().toString());
          node.set("data", r.getEvent());
          node.set("time", time);
          return writer.writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
      };

      HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(logger::debug);
      interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

       OkHttpClient httpClient = new OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .build();

      this.client = new HoneycombOkHTTPClient<>(
        httpClient, new JsonFactory(), writeKey, dataSet, defaultEncodeFunction);
    }

    @Override
    public HoneycombClient get() {
      return client;
    }
  }
}
