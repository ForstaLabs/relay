package io.forsta.securesms.util;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.UiThread;

import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.storage.TextSecureSessionStore;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.util.concurrent.ListenableFuture;
import io.forsta.securesms.util.concurrent.SettableFuture;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;

public class IdentityUtil {

  @UiThread
  public static ListenableFuture<Optional<IdentityKey>> getRemoteIdentityKey(final Context context,
                                                                             final MasterSecret masterSecret,
                                                                             final Recipient recipient)
  {
    final SettableFuture<Optional<IdentityKey>> future = new SettableFuture<>();

    new AsyncTask<Recipient, Void, Optional<IdentityKey>>() {
      @Override
      protected Optional<IdentityKey> doInBackground(Recipient... recipient) {
        SessionStore sessionStore = new TextSecureSessionStore(context, masterSecret);
        String addr = recipient[0].getNumber();
        List<Integer> devices = sessionStore.getDeviceSessions(addr);
        for (int device : devices) {
          SessionRecord record = sessionStore.loadSession(new SignalProtocolAddress(addr, device));
          if (record != null) {
            return Optional.fromNullable(record.getSessionState().getRemoteIdentityKey());
          }
        }
        return Optional.absent();
      }

      @Override
      protected void onPostExecute(Optional<IdentityKey> result) {
        future.set(result);
      }
    }.execute(recipient);

    return future;
  }

}
