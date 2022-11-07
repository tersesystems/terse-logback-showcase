package models;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import play.libs.Json;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public class Cat {

  // {"tags":["watter cat","gif"],"createdAt":"2018-02-08T14:48:11.639Z","updatedAt":"2022-10-11T07:52:32.305Z","validated":true,"owner":"Paff","file":"5a7c632b8ea758000f978214.gif","mimetype":"image/gif","size":null,"_id":"XU08gRodhhZFZqBj","url":"/cat/XU08gRodhhZFZqBj"}

  private final List<String> tags;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final String owner;
  private final String file;
  private final String mimetype;

  private final String url;

  Cat(List<String> tags, Instant createdAt, Instant updatedAt, String owner, String file, String mimetype, String url) {
    this.tags = tags;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.owner = owner;
    this.file = file;
    this.mimetype = mimetype;
    this.url = url;
  }

  public static Cat parse(JsonNode json) throws IOException {
    // https://cataas.com/#/
    ObjectMapper mapper = Json.mapper();
    ObjectReader listReader = mapper.readerFor(new TypeReference<List<String>>() {
    });
    List<String> tags = listReader.readValue(json.get("tags"));
    ObjectReader instantReader = mapper.readerFor(new TypeReference<Instant>() {
    });
    Instant createdAt = instantReader.readValue(json.get("createdAt"));
    Instant updatedAt = instantReader.readValue(json.get("updatedAt"));
    String owner = json.get("owner").asText();
    String file = json.get("file").asText();
    String mimetype = json.get("mimetype").asText();
    String url = json.get("url").asText();

    return new Cat(tags, createdAt, updatedAt, owner, file, mimetype, url);
  }

  public List<String> tags() {
    return tags;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public String owner() {
    return owner;
  }

  public String file() {
    return file;
  }

  public String mimetype() {
    return mimetype;
  }

  public String url() {
    return url;
  }


}
