package io.forsta.ccsm.util;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jlewis on 9/23/17.
 */

public class InvalidMessagePayloadException extends Exception {

  List<JSONException> jsonExceptions = new ArrayList<>();

  public InvalidMessagePayloadException(String message) {
    super(message);
  }
}
