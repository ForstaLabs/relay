package io.forsta.securesms.crypto;

import android.content.Context;
import android.support.annotation.NonNull;

import io.forsta.securesms.crypto.storage.TextSecureSessionStore;
import io.forsta.securesms.recipients.Recipient;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;

public class SessionUtil {

  public static boolean hasSession(Context context, MasterSecret masterSecret, Recipient recipient) {
    return hasSession(context, masterSecret, recipient.getNumber());
  }

  public static boolean hasSession(Context context, MasterSecret masterSecret, @NonNull String addr) {
    SessionStore sessionStore = new TextSecureSessionStore(context, masterSecret);
    List<Integer> devices = sessionStore.getDeviceSessions(addr);
    for (int device : devices) {
      if (sessionStore.containsSession(new SignalProtocolAddress(addr, device))) {
        return true;
      }
    }
    return false;
  }
}
