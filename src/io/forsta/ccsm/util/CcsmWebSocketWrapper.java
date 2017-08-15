package io.forsta.ccsm.util;

import android.content.Context;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.internal.ws.WebSocket;
import com.squareup.okhttp.internal.ws.WebSocketListener;

import java.io.IOException;

import io.forsta.ccsm.ForstaPreferences;
import okio.BufferedSource;

/**
 * Created by jlewis on 8/15/17.
 */

public class CcsmWebSocketWrapper implements WebSocketListener {

  private WebSocket webSocket;
  private String uri;
  private String authKey;


  public CcsmWebSocketWrapper(Context context, String uri) {
    this.uri = uri;
    authKey = ForstaPreferences.getRegisteredKey(context);

  }

  public void connect(final int timeout) {
    new Thread() {
      @Override
      public void run() {

        OkHttpClient client = new OkHttpClient();
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.addHeader("Authorization", "JWT " + authKey);
        requestBuilder.url(uri);
        webSocket = WebSocket.newWebSocket(client, requestBuilder.build());
        try {
          Response response = webSocket.connect(CcsmWebSocketWrapper.this);
          ResponseBody body = response.body();
          webSocket.close(0, "Normal shutdown");

        } catch (IOException e) {
          e.printStackTrace();
        }

      }
    }.start();
  }

  public void disconnect() {
    if (webSocket != null && !webSocket.isClosed()) {
      try {
        webSocket.close(0, "Normal close");
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void onMessage(BufferedSource payload, WebSocket.PayloadType type) throws IOException {

  }

  @Override
  public void onClose(int code, String reason) {

  }

  @Override
  public void onFailure(IOException e) {

  }
}
