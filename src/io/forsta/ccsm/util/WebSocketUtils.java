package io.forsta.ccsm.util;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.signalservice.internal.websocket.WebSocketProtos;

import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.util.TextSecurePreferences;
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
  private String url;
  private final String authKey;
  private MessageCallback callback;
  public boolean socketOpen = false;
  private Handler messageHandler;
  private static WebSocketUtils instance;
  private static final Object lock = new Object();

  private WebSocketUtils(Context context, String url, MessageCallback callback) {
    this.authKey = ForstaPreferences.getRegisteredKey(context);
    this.url = TextSecurePreferences.getServer(context) + url;
    this.url = this.url.replace("https", "wss");
    this.callback = callback;
    client = new OkHttpClient().newBuilder().readTimeout(3, TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
  }

  public static WebSocketUtils getInstance(Context context, String url, MessageCallback callback) {
    synchronized (lock) {
      if (instance == null)
        instance = new WebSocketUtils(context, url, callback);

      return instance;
    }
  }

  public void connect() {
    Request.Builder request = new Request.Builder();
    request.url(url);
    socket = client.newWebSocket(request.build(), new SocketListener());
    messageHandler = new Handler(new Handler.Callback() {
      @Override
      public boolean handleMessage(Message message) {
        callback.onMessage((String)message.obj);
        callback.onStatusChanged(socketOpen);
        return true;
      }
    });
  }

  public void disconnect() {
    socket.close(1000, "Bye");
    messageHandler.removeCallbacksAndMessages(null);
  }

  private synchronized void setSocketState(boolean state) {
    socketOpen = state;
  }

  private void handleMessage(String text) {
    Message message = messageHandler.obtainMessage(0, text);
    messageHandler.sendMessage(message);
  }

  private class SocketListener extends WebSocketListener {
    @Override
    public void onOpen(WebSocket webSocket, Response response) {
      Log.d(TAG, "Socket open");
      setSocketState(true);
      handleMessage("Socket open");
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
      Log.d(TAG, text);
      handleMessage(text);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
      Log.d(TAG, "New byte stream: " + bytes.size());
      try {
        WebSocketProtos.WebSocketMessage message = WebSocketProtos.WebSocketMessage.parseFrom(bytes.toByteArray());
        if (message.getType().equals(WebSocketProtos.WebSocketMessage.Type.REQUEST)) {
          WebSocketProtos.WebSocketRequestMessage request = message.getRequest();
          String path = request.getPath();
          String verb = request.getVerb();
          com.google.protobuf.ByteString requestBytes = request.getBody();
          // Now decode with ProvisionUuid.proto. Add to libsignal-service.
          if (path.equals("/v1/address") && verb.equals("PUT")) {
            
          }
        } else if (message.getType().equals(WebSocketProtos.WebSocketMessage.Type.RESPONSE)) {

        }
      } catch (InvalidProtocolBufferException e) {
        e.printStackTrace();
      }
      handleMessage(bytes.utf8());
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
      Log.d(TAG, "Socket closed");
      setSocketState(false);
      handleMessage("Socket closed");
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
      Log.d(TAG, "Socket Failed");
      setSocketState(false);
      handleMessage("Socket failed");
    }
  }

  public interface MessageCallback {
    void onMessage(String message);
    void onStatusChanged(boolean connected);
  }
}
