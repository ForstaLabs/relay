package io.forsta.securesms.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.support.v4.util.ArraySet;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import io.forsta.securesms.ApplicationPreferencesActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.notifications.MessageNotifier;
import io.forsta.securesms.util.TextSecurePreferences;

public class NotificationsPreferenceFragment extends ListSummaryPreferenceFragment {

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    masterSecret = getArguments().getParcelable("master_secret");
    addPreferencesFromResource(R.xml.preferences_notifications);

    this.findPreference(TextSecurePreferences.NOTIFICATION_FILTER)
        .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object o) {
            MultiSelectListPreference listPref   = (MultiSelectListPreference) preference;
            listPref.setSummary(getNotificationDisplayValues(listPref, (Set<String>) o));
            return true;
          }
        });
    this.findPreference(TextSecurePreferences.LED_COLOR_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(TextSecurePreferences.LED_BLINK_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(TextSecurePreferences.RINGTONE_PREF)
        .setOnPreferenceChangeListener(new RingtoneSummaryListener());
    this.findPreference(TextSecurePreferences.REPEAT_ALERTS_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(TextSecurePreferences.NOTIFICATION_PRIVACY_PREF)
        .setOnPreferenceChangeListener(new NotificationPrivacyListener());

    MultiSelectListPreference notificationFilter = (MultiSelectListPreference) findPreference(TextSecurePreferences.NOTIFICATION_FILTER);
    notificationFilter.setSummary(getNotificationDisplayValues(notificationFilter, notificationFilter.getValues()));
    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.LED_COLOR_PREF));
    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.LED_COLOR_PREF));
    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.LED_BLINK_PREF));
    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.REPEAT_ALERTS_PREF));
    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.NOTIFICATION_PRIVACY_PREF));
    initializeRingtoneSummary((RingtonePreference) findPreference(TextSecurePreferences.RINGTONE_PREF));
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__notifications);
  }

  private class RingtoneSummaryListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      String value = (String) newValue;

      if (TextUtils.isEmpty(value)) {
        preference.setSummary(R.string.preferences__silent);
      } else {
        Ringtone tone = RingtoneManager.getRingtone(getActivity(), Uri.parse(value));
        if (tone != null) {
          preference.setSummary(tone.getTitle(getActivity()));
        }
      }

      return true;
    }
  }

  private void initializeRingtoneSummary(RingtonePreference pref) {
    RingtoneSummaryListener listener =
      (RingtoneSummaryListener) pref.getOnPreferenceChangeListener();
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

    listener.onPreferenceChange(pref, sharedPreferences.getString(pref.getKey(), ""));
  }

  public static CharSequence getSummary(Context context) {
    final int onCapsResId   = R.string.ApplicationPreferencesActivity_On;
    final int offCapsResId  = R.string.ApplicationPreferencesActivity_Off;

    return context.getString(TextSecurePreferences.isNotificationsEnabled(context) ? onCapsResId : offCapsResId);
  }

  private class NotificationPrivacyListener extends ListSummaryListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          MessageNotifier.updateNotification(getActivity(), masterSecret);
          return null;
        }
      }.execute();

      return super.onPreferenceChange(preference, value);
    }

  }

  private String getNotificationDisplayValues(MultiSelectListPreference notificationPreference, Set<String> values) {
    if (values.size() == 0) {
      return "Show all notifications";
    }
    List<CharSequence> selectedValues = new ArrayList<>();
    for (String value : values) {
      int i = notificationPreference.findIndexOfValue(value);
      selectedValues.add(notificationPreference.getEntries()[i]);
    }
    Collections.reverse(selectedValues);
    return TextUtils.join(", ", selectedValues);
  }
}
