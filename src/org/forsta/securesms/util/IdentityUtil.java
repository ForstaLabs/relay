package org.forsta.securesms.util;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.UiThread;

import org.forsta.securesms.crypto.MasterSecret;
import org.forsta.securesms.crypto.storage.TextSecureSessionStore;
import org.forsta.securesms.recipients.Recipient;
import org.forsta.securesms.util.concurrent.ListenableFuture;
import org.forsta.securesms.util.concurrent.SettableFuture;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

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
        SessionStore          sessionStore   = new TextSecureSessionStore(context, masterSecret);
        SignalProtocolAddress axolotlAddress = new SignalProtocolAddress(recipient[0].getNumber(), SignalServiceAddress.DEFAULT_DEVICE_ID);
        SessionRecord         record         = sessionStore.loadSession(axolotlAddress);

        if (record == null) {
          return Optional.absent();
        }

        return Optional.fromNullable(record.getSessionState().getRemoteIdentityKey());
      }

      @Override
      protected void onPostExecute(Optional<IdentityKey> result) {
        future.set(result);
      }
    }.execute(recipient);

    return future;
  }

}
