package org.folio.tag.repository.resources;

public class HoldingsTableConstants {
  public static final String HOLDINGS_TABLE = "holdings";
  public static final String HOLDINGS_STATUS_TABLE = "holdings_status";
  public static final String ID_COLUMN = "id";
  public static final String JSONB_COLUMN = "jsonb";
  public static final String HOLDINGS_FIELD_LIST = String.format("%s, %s", ID_COLUMN, JSONB_COLUMN);
  public static final String INSERT_OR_UPDATE_HOLDINGS_STATEMENT =
    "INSERT INTO %s(" + HOLDINGS_FIELD_LIST + ") VALUES %s" +
      "ON CONFLICT (" + ID_COLUMN + ") DO NOTHING";
  public static final String REMOVE_FROM_HOLDINGS = "DELETE FROM %s;";

  public static final String GET_HOLDINGS_BY_IDS = "SELECT * from %s WHERE id IN (%s);";

  public static final String GET_LOADING_STATUS = "SELECT * from %s;";
  public static final String UPDATE_LOADING_STATUS = "UPDATE %s SET " + JSONB_COLUMN +
    "= '%s' WHERE id = '1b3d3881-656b-40e1-87ed-a98e2ae5732d'";

  public static final String INSERT_UPDATE_LOADING_STATUS = "INSERT INTO %s (" + HOLDINGS_FIELD_LIST +
    ") VALUES (%s) ON CONFLICT (" + ID_COLUMN + ") DO UPDATE SET " + JSONB_COLUMN + " = ?;";

  private HoldingsTableConstants() { }
}
