package io.forsta.securesms.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

import io.forsta.securesms.R;
import io.forsta.securesms.database.RecipientPreferenceDatabase;
import io.forsta.securesms.preferences.NotificationPrivacyPreference;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;

public abstract class AbstractNotificationBuilder extends NotificationCompat.Builder {

  protected Context                       context;
  protected NotificationPrivacyPreference privacy;
  public static final String CHANNEL_ID = "forsta_channel_01";
  protected NotificationChannel channel;

  public AbstractNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context);

    this.context = context;
    this.privacy = privacy;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      channel = new NotificationChannel(CHANNEL_ID,
          "Channel human readable title",
          NotificationManager.IMPORTANCE_DEFAULT);
      channel.setDescription("Forsta Channel");
      channel.setName("Forsta");
    }
    this.setChannelId(CHANNEL_ID);

    setLed();
  }

  public NotificationChannel getChannel() {
    return channel;
  }

  protected CharSequence getStyledMessage(@NonNull Recipient recipient, @Nullable CharSequence message) {
    SpannableStringBuilder builder = new SpannableStringBuilder();
    builder.append(Util.getBoldedString(recipient.toShortString()));
    builder.append(": ");
    builder.append(message == null ? "" : message);

    return builder;
  }

  public void setAlarms(@Nullable Uri ringtone, RecipientPreferenceDatabase.VibrateState vibrate) {
    String  defaultRingtoneName = TextSecurePreferences.getNotificationRingtone(context);
    boolean defaultVibrate      = TextSecurePreferences.isNotificationVibrateEnabled(context);

    if      (ringtone != null)                        setSound(ringtone);
    else if (!TextUtils.isEmpty(defaultRingtoneName)) setSound(Uri.parse(defaultRingtoneName));

    if (vibrate == RecipientPreferenceDatabase.VibrateState.ENABLED ||
        (vibrate == RecipientPreferenceDatabase.VibrateState.DEFAULT && defaultVibrate))
    {
      setDefaults(Notification.DEFAULT_VIBRATE);
    }
  }

  private void setLed() {
    String ledColor              = TextSecurePreferences.getNotificationLedColor(context);
    String ledBlinkPattern       = TextSecurePreferences.getNotificationLedPattern(context);
    String ledBlinkPatternCustom = TextSecurePreferences.getNotificationLedPatternCustom(context);

    if (!ledColor.equals("none")) {
      String[] blinkPatternArray = parseBlinkPattern(ledBlinkPattern, ledBlinkPatternCustom);

      setLights(Color.parseColor(ledColor),
                Integer.parseInt(blinkPatternArray[0]),
                Integer.parseInt(blinkPatternArray[1]));
    }
  }

  public void setTicker(@NonNull Recipient recipient, @Nullable CharSequence message) {
    if (privacy.isDisplayMessage()) {
      setTicker(getStyledMessage(recipient, message));
    } else if (privacy.isDisplayContact()) {
      setTicker(getStyledMessage(recipient, context.getString(R.string.AbstractNotificationBuilder_new_message)));
    } else {
      setTicker(context.getString(R.string.AbstractNotificationBuilder_new_message));
    }
  }

  private String[] parseBlinkPattern(String blinkPattern, String blinkPatternCustom) {
    if (blinkPattern.equals("custom"))
      blinkPattern = blinkPatternCustom;

    return blinkPattern.split(",");
  }
}
