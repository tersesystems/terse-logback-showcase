package modules;

import com.typesafe.config.Config;
import logging.CustomRequestFactory;
import play.ApplicationLoader;
import play.Environment;
import play.api.mvc.request.RequestFactory;
import play.inject.Binding;
import play.inject.Module;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;

import java.util.Collections;
import java.util.List;

/**
 * An application loader that swaps out the request factory to
 * use StartTimeRequestFactory.
 *
 * We need to use the app loader rather than the module because
 * the error handler is special, see inline for details.
 */
public class CustomApplicationLoader extends GuiceApplicationLoader {

  @Override
  public GuiceApplicationBuilder builder(ApplicationLoader.Context context) {
    return initialBuilder
        .in(context.environment())
        .loadConfig(context.initialConfig())
        .overrides(overrides(context))
        .overrides(new CustomRequestFactoryModule());
  }

  // When there's an exception, the unmodified request (which has not passed through
  // filters) is passed to the error handler -- so if we want to insert any
  // attributes that are available to the error handler, we have to do so
  // from the request factory, which means we're going to override the bindings
  // on the application loader.
  static class CustomRequestFactoryModule extends Module {
    @Override
    public List<Binding<?>> bindings(Environment environment, Config config) {
      return Collections.singletonList(
          bindClass(RequestFactory.class).to(CustomRequestFactory.class)
      );
    }
  }
}