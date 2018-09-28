package io.forsta.securesms.service;

import android.content.Intent;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import io.forsta.securesms.R;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.mms.SlideDeck;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.sms.OutgoingTextMessage;
import io.forsta.securesms.util.Rfc5724Uri;
import org.whispersystems.libsignal.util.guava.Optional;

import java.net.URISyntaxException;
import java.net.URLDecoder;

import io.forsta.securesms.database.RecipientPreferenceDatabase;

public class QuickResponseService extends MasterSecretIntentService {

  private static final String TAG = QuickResponseService.class.getSimpleName();

  public QuickResponseService() {
    super("QuickResponseService");
  }

  @Override
  protected void onHandleIntent(Intent intent, @Nullable MasterSecret masterSecret) {
    if (!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(intent.getAction())) {
      Log.w(TAG, "Received unknown intent: " + intent.getAction());
      return;
    }

    if (masterSecret == null) {
      Log.w(TAG, "Got quick response request when locked...");
      Toast.makeText(this, R.string.QuickResponseService_quick_response_unavailable_when_Signal_is_locked, Toast.LENGTH_LONG).show();
      return;
    }

    try {
      Rfc5724Uri uri        = new Rfc5724Uri(intent.getDataString());
      String     content    = intent.getStringExtra(Intent.EXTRA_TEXT);
      String     numbers    = uri.getPath();
      if(numbers.contains("%")){
        numbers = URLDecoder.decode(numbers);
      }

      Recipients recipients     = RecipientFactory.getRecipientsFromString(this, numbers, false);
      Optional<RecipientPreferenceDatabase.RecipientsPreferences> preferences    = DatabaseFactory.getRecipientPreferenceDatabase(this).getRecipientsPreferences(recipients.getIds());
      int                             subscriptionId = preferences.isPresent() ? preferences.get().getDefaultSubscriptionId().or(-1) : -1;
      long                            expiresIn      = preferences.isPresent() ? preferences.get().getExpireMessages() * 1000 : 0;

      if (!TextUtils.isEmpty(content)) {
        if (recipients.isSingleRecipient()) {
//          MessageSender.send(this, masterSecret, new OutgoingTextMessage(recipients, content, expiresIn, subscriptionId), -1, false);
        } else {
//          MessageSender.send(this, masterSecret, new OutgoingMediaMessage(recipients, new SlideDeck(), content, System.currentTimeMillis(), subscriptionId, expiresIn, ThreadDatabase.DistributionTypes.DEFAULT), -1, false);
        }
      }
    } catch (URISyntaxException e) {
      Toast.makeText(this, R.string.QuickResponseService_problem_sending_message, Toast.LENGTH_LONG).show();
      Log.w(TAG, e);
    }
  }
}
