package io.forsta.securesms.gcm;

import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;

import org.whispersystems.libsignal.util.guava.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class FcmUtil {

  private static final String TAG = FcmUtil.class.getSimpleName();

  /**
   * Retrieves the current FCM token. If one isn't available, it'll be generated.
   */
  @WorkerThread
  public static Optional<String> getToken() {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> token = new AtomicReference<>(null);

    FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(task -> {
      if (task.isSuccessful() && task.getResult() != null && !TextUtils.isEmpty(task.getResult().getToken())) {
        token.set(task.getResult().getToken());
      } else {
        Log.w(TAG, "Failed to get the token.", task.getException());
      }

      latch.countDown();
    });

    try {
      latch.await();
    } catch (InterruptedException e) {
      Log.w(TAG, "Was interrupted while waiting for the token.");
    }

    return Optional.fromNullable(token.get());
  }
}

