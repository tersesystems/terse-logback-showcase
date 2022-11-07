package services;

import com.fasterxml.jackson.databind.JsonNode;
import models.Cat;
import play.libs.ws.WSClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.CompletionStage;

@Singleton
public class CatService {

  private final WSClient ws;

  @Inject
  public CatService(WSClient wsClient) {
    this.ws = wsClient;
  }

  public CompletionStage<Cat> getCat() {
    return ws.url("https://cataas.com/cat?json=true").get().thenApply(response -> {
      JsonNode node = response.asJson();
      try {
        return Cat.parse(node);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

}
