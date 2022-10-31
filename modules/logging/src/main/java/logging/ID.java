package logging;

import play.api.mvc.RequestHeader;
import play.mvc.Http;

public class ID {

  public static String get(Http.RequestHeader request) {
    return request.attrs().get(Attrs.ID);
  }

  public static String get(RequestHeader request) {
    return request.attrs().apply(Attrs.ID.asScala());
  }

}
