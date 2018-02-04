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
  private final String authKey;
  private MessageCallbacks callback;
  public boolean socketOpen = false;
  private Context context;
  private Handler messageHandler;
  private static WebSocketUtils instance;
  private static final Object lock = new Object();

  private WebSocketUtils(Context context, MessageCallbacks callback) {
    this.authKey = ForstaPreferences.getRegisteredKey(context);
    this.callback = callback;
    this.context = context;
    client = new OkHttpClient().newBuilder().readTimeout(3, TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
  }

  public static WebSocketUtils getInstance(Context context, MessageCallbacks callback) {
    synchronized (lock) {
      if (instance == null)
        instance = new WebSocketUtils(context, callback);

      return instance;
    }
  }

  public void connect(String url) {
    Request.Builder request = new Request.Builder();
    url = BuildConfig.SIGNAL_API_URL + url;
    url = url.replace("https", "wss");
    request.url(url);
    socket = client.newWebSocket(request.build(), new SocketListener());
//    messageHandler = new Handler(new Handler.Callback() {
//      @Override
//      public boolean handleMessage(Message message) {
//        callback.onSocketMessage((WebSocketProtos.WebSocketRequestMessage) message.obj);
//        callback.onStatusChanged(socketOpen);
//        return true;
//      }
//    });
  }

  public void disconnect() {
    socket.close(1000, "Bye");
//    messageHandler.removeCallbacksAndMessages(null);
  }

  private synchronized void setSocketState(boolean state) {
    socketOpen = state;
  }

  private void handleThreadMessage(WebSocketProtos.WebSocketRequestMessage socketMessage) {
    Message message = messageHandler.obtainMessage(0, socketMessage);
    messageHandler.sendMessage(message);
  }

  private class SocketListener extends WebSocketListener {
    @Override
    public void onOpen(WebSocket webSocket, Response response) {
      Log.d(TAG, "Socket open");
      setSocketState(true);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
      Log.d(TAG, "Got String message from socket");
      Log.d(TAG, text);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
      try {
        WebSocketProtos.WebSocketMessage message = WebSocketProtos.WebSocketMessage.parseFrom(bytes.toByteArray());
        if (message.getType().equals(WebSocketProtos.WebSocketMessage.Type.REQUEST)) {
          if (callback != null) {
            callback.onSocketMessage(message.getRequest());
          }
        } else if (message.getType().equals(WebSocketProtos.WebSocketMessage.Type.RESPONSE)) {
          // TODO implement
        }
      } catch (InvalidProtocolBufferException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
      Log.d(TAG, "Socket closed");
      setSocketState(false);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
      Log.d(TAG, "Socket Failed");
      setSocketState(false);
    }
  }

  public interface MessageCallbacks {
    void onSocketMessage(WebSocketProtos.WebSocketRequestMessage message);
    void onStatusChanged(boolean connected);
  }
}
