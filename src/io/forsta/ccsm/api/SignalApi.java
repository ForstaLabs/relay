package io.forsta.ccsm.api;

import android.content.Context;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.signalservice.internal.util.Base64;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos;

import java.util.Arrays;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.ProvisioningCipher;
import io.forsta.ccsm.util.WebSocketUtils;
import io.forsta.securesms.crypto.IdentityKeyUtil;

/**
 * Created by john on 2/2/2018.
 */


public class SignalApi {
  private static final String TAG = SignalApi.class.getSimpleName();

  private WebSocketUtils webSocket;
  private static SignalApi instance;
  private Context context;
  private static final Object lock = new Object();
  private ProvisionCallbacks callbacks;

  private SignalApi(Context context) {
    this.context = context;
  }

  public static SignalApi getInstance(Context context) {
    synchronized (lock) {
      if (instance == null)
        instance = new SignalApi(context);

      return instance;
    }
  }

  public void setProvisionCallbacks(ProvisionCallbacks callbacks) {
    this.callbacks = callbacks;
  }

  public void autoProvision() {
    webSocket = WebSocketUtils.getInstance(context, new WebSocketUtils.MessageCallbacks() {
      @Override
      public void onSocketMessage(WebSocketProtos.WebSocketRequestMessage request) {
        String path = request.getPath();
        String verb = request.getVerb();
         IdentityKeyPair identityKeys  = IdentityKeyUtil.getIdentityKeyPair(context);
        ECPublicKey ourPubKey = identityKeys.getPublicKey().getPublicKey();
        ECPrivateKey ourPrivKey = identityKeys.getPrivateKey();
//        ECKeyPair keyPair = Curve.generateKeyPair();
//        ECPublicKey ourPubKey = keyPair.getPublicKey();
//        ECPrivateKey ourPrivKey = keyPair.getPrivateKey();

        Log.w(TAG, "Our Public and Private keys");
        Log.w(TAG, Arrays.toString(ourPubKey.serialize()));
        Log.w(TAG, Arrays.toString(ourPrivKey.serialize()));

        if (path.equals("/v1/address") && verb.equals("PUT")) {
          Log.w(TAG, "Received address");
          try {
            final ProvisioningProtos.ProvisioningUuid proto = ProvisioningProtos.ProvisioningUuid.parseFrom(request.getBody());
            byte[] serializedPublicKey = ourPubKey.serialize();
            final String encodedKey = Base64.encodeBytes(serializedPublicKey);
            CcsmApi.provisionRequest(context, proto.getUuid(), encodedKey);

          } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
          }
        } else if (path.equals("/v1/message") && verb.equals("PUT")) {
          Log.w(TAG, "Received message");
          try {
            org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionEnvelope envelope = org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionEnvelope.parseFrom(request.getBody());
            ProvisioningCipher provisionCipher = new ProvisioningCipher(null);
            org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionMessage provisionMessage = provisionCipher.decrypt(envelope, ourPrivKey);
            if (provisionMessage != null) {
              if (!provisionMessage.getNumber().equals(ForstaPreferences.getUserId(context))) { // or TextSecurePreferences.getNumber()
                Log.w(TAG, "Received provision message from unknown address");
              }
              Log.w(TAG, "Provisioning message content");
              Log.w(TAG, provisionMessage.getNumber());
              Log.w(TAG, provisionMessage.getProvisioningCode());
              Log.w(TAG, "Private key");
              Log.w(TAG, Arrays.toString(provisionMessage.getIdentityKeyPrivate().toByteArray())); //My private key

              // Not this
              // accountManager.addDevice(provisionMessage.getNumber(), theirPublicKey, identityKeyPair, provisionMessage.getProvisioningCode());
            } else {
              Log.w(TAG, "Failed to decrypt provision message");
            }
          } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
          }
          webSocket.disconnect();
        }
      }

      @Override
      public void onStatusChanged(boolean connected) {
        Log.w(TAG, "Socket " + (connected ? "Open" : "Closed"));
      }
    });
    webSocket.connect("/v1/websocket/provisioning/");
  }

  public interface ProvisionCallbacks {
    void onStartProvisioning(String uuid);
    void onReceiveProvisionMessage();
    void onComplete();
  }
}
