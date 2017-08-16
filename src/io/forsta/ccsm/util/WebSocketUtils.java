package io.forsta.ccsm.util;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.forsta.ccsm.ForstaPreferences;
import okio.ByteString;

/**
 * Created by jlewis on 8/16/17.
 */

public class WebSocketUtils {
  private final String TAG = WebSocketUtils.class.getSimpleName();
  private WebSocket socket;
  private OkHttpClient client;
  private final String uri;
  private final String authKey;
  private MessageCallback callback;
  public boolean socketOpen = false;
  private Handler messageHandler;

  public WebSocketUtils(Context context, String uri, MessageCallback callback) {
    this.authKey = ForstaPreferences.getRegisteredKey(context);
    this.uri =  uri + authKey + "/";
    this.callback = callback;
    client = new OkHttpClient().newBuilder().readTimeout(3, TimeUnit.SECONDS).retryOnConnectionFailure(false).build();
  }

  public void connect() {
    Request.Builder request = new Request.Builder();
    request.url(uri);
    socket = client.newWebSocket(request.build(), new SocketListener());
    messageHandler = new Handler(new Handler.Callback() {
      @Override
      public boolean handleMessage(Message message) {
        callback.onMessage((String)message.obj);
        return true;
      }
    });
  }

  public void disconnect() {
    socket.close(1000, "Bye");
    messageHandler.removeCallbacksAndMessages(null);
  }

  private class SocketListener extends WebSocketListener {
    @Override
    public void onOpen(WebSocket webSocket, Response response) {
      Log.d(TAG, "Socket open");
      socketOpen = true;
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
      Log.d(TAG, text);
      Message message = messageHandler.obtainMessage(0, text);
      messageHandler.sendMessage(message);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
      Log.d(TAG, "New byte stream");
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
      Log.d(TAG, "Socket closed");
      socketOpen = false;
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
      Log.d(TAG, "Socket Failed");
      socketOpen = false;
    }
  }

  public interface MessageCallback {
    void onMessage(String message);
    void onStatusChanged(boolean connected);
  }
}
