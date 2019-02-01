package org.folio.tag.repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.Tags;
import org.folio.rest.persist.PostgresClient;
import org.folio.tag.RecordType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;

@Component
public class TagRepository {
  public static final String TABLE_NAME = "tags";
  public static final String TAG_COLUMN = "tag";
  public static final String RECORD_ID_COLUMN = "record_id";
  public static final String RECORD_TYPE_COLUMN = "record_type";

  public static final String SELECT_TAG_VALUES_BY_ID_AND_TYPE =
    "SELECT " + TAG_COLUMN + " FROM %s "
    + "WHERE " + RECORD_ID_COLUMN + "=? AND " + RECORD_TYPE_COLUMN + "=?";
  private Vertx vertx;

  @Autowired
  public TagRepository(Vertx vertx) {
    this.vertx = vertx;
  }

  public CompletableFuture<Tags> getTags(String tenantId, String recordId, RecordType recordType){
    CompletableFuture<Tags> future = new CompletableFuture<>();

    JsonArray parameters = new JsonArray();
    parameters.add(recordId);
    parameters.add(recordType.getValue());

    PostgresClient.getInstance(vertx, tenantId)
      .select(
        String.format(SELECT_TAG_VALUES_BY_ID_AND_TYPE, getTableName(tenantId)),
        parameters,
        result -> readTags(result, future)
      );
    return future;
  }

  private void readTags(AsyncResult<ResultSet> result, CompletableFuture<Tags> future) {
    ResultSet resultSet = result.result();
    List<String> tagValues = resultSet.getRows().stream()
      .map(row -> row.getString(TAG_COLUMN))
      .collect(Collectors.toList());
    future.complete(new Tags().withTagList(tagValues));
  }

  private String getTableName(String tenantId) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + TABLE_NAME;
  }
}
