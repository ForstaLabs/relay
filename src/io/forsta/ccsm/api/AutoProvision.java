package io.forsta.ccsm.api;

import android.content.Context;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.curve25519.JavaCurve25519Provider;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.signalservice.internal.util.Base64;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.ProvisioningCipher;
import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.ccsm.util.WebSocketUtils;
import io.forsta.securesms.crypto.IdentityKeyUtil;
import io.forsta.securesms.push.TextSecureCommunicationFactory;

/**
 * Created by john on 2/2/2018.
 */

// Rename this to AutoProvision or something else.
public class AutoProvision {
  private static final String TAG = AutoProvision.class.getSimpleName();

  private WebSocketUtils webSocket;
  private static AutoProvision instance;
  private Context context;
  private static final Object lock = new Object();
  private ProvisionCallbacks callbacks;

  private AutoProvision(Context context) {
    this.context = context;
  }

  public static AutoProvision getInstance(Context context) {
    synchronized (lock) {
      if (instance == null)
        instance = new AutoProvision(context);

      return instance;
    }
  }

  public void setProvisionCallbacks(ProvisionCallbacks callbacks) {
    this.callbacks = callbacks;
  }

  public void start() {
    webSocket = WebSocketUtils.getInstance(context, new WebSocketUtils.MessageCallbacks() {
      @Override
      public void onSocketMessage(WebSocketProtos.WebSocketRequestMessage request) {
        String path = request.getPath();
        String verb = request.getVerb();

        IdentityKeyPair identityKeys  = IdentityKeyUtil.getIdentityKeyPair(context);
        ECPublicKey ourPubKey = identityKeys.getPublicKey().getPublicKey();
        ECPrivateKey ourPrivKey = identityKeys.getPrivateKey();

        if (path.equals("/v1/address") && verb.equals("PUT")) {
          Log.w(TAG, "Auto Provision. Received ephemeral address.");

          try {
            final ProvisioningProtos.ProvisioningUuid proto = ProvisioningProtos.ProvisioningUuid.parseFrom(request.getBody());
            byte[] serializedPublicKey = ourPubKey.serialize();
            final String encodedKey = Base64.encodeBytes(serializedPublicKey);
            CcsmApi.provisionRequest(context, proto.getUuid(), encodedKey);

          } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
          }

        } else if (path.equals("/v1/message") && verb.equals("PUT")) {
          Log.w(TAG, "Received Provision Envelope message");

          try {
            org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionEnvelope envelope = org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionEnvelope.parseFrom(request.getBody());
            ProvisioningCipher provisionCipher = new ProvisioningCipher(null);
            org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionMessage provisionMessage = provisionCipher.decrypt(envelope, ourPrivKey);

            if (provisionMessage != null) {
              webSocket.disconnect();
              Log.w(TAG, "New Provision Message");
              KeyProvider keyProvider = new KeyProvider();
              ECPrivateKey newPrivateKey = Curve.decodePrivatePoint(provisionMessage.getIdentityKeyPrivate().toByteArray());
              byte[] pubKey = keyProvider.generatePublicKey(newPrivateKey.serialize());
              byte[] typedPublicKey = IdentityKeyUtil.addKeyType(pubKey);
              ECPublicKey newPublicKey = Curve.decodePoint(typedPublicKey, 0);
              IdentityKeyUtil.updateKeys(context, newPrivateKey, newPublicKey);
              provisioningComplete(provisionMessage);
            } else {
              provisioningFailed("Unable to decrypt provision message");
            }

          } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            provisioningFailed(e.getMessage());
          } catch (InvalidKeyException e) {
            e.printStackTrace();
            provisioningFailed(e.getMessage());
          }

          if (webSocket.socketOpen) {
            webSocket.disconnect();
          }
        }
      }

      @Override
      public void onStatusChanged(boolean connected) {
        Log.w(TAG, "Socket " + (connected ? "Open" : "Closed"));
      }
    });
    webSocket.connect("/v1/websocket/provisioning/");
  }

  private void provisioningComplete(org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionMessage provisionMessage) {
    if (callbacks != null) {
      callbacks.onComplete(provisionMessage);
    }
  }

  private void provisioningFailed(String message) {
    if (callbacks != null) {
      callbacks.onFailure(message);
    }
  }

  public interface ProvisionCallbacks {
    void onComplete(org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionMessage provisionMessage);
    void onFailure(String message);
  }

  // XXX This is here to expose generatePublicKey method.
  // Move to libsignal-service
  class KeyProvider extends JavaCurve25519Provider {
    public KeyProvider() {
      super();
    }
  }
}
