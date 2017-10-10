package io.forsta.securesms.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.preference.PreferenceFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import io.forsta.ccsm.api.model.ForstaDistribution;
import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.ccsm.DashboardActivity;
import io.forsta.ccsm.ForstaLogSubmitActivity;
import io.forsta.ccsm.ForstaPreferences;
import io.forsta.securesms.ApplicationPreferencesActivity;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.LogSubmitActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.RegistrationActivity;
import io.forsta.securesms.contacts.ContactAccessor;
import io.forsta.securesms.contacts.ContactIdentityManager;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.push.TextSecureCommunicationFactory;
import io.forsta.securesms.util.task.ProgressDialogAsyncTask;
import io.forsta.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;

import java.io.IOException;

public class AdvancedPreferenceFragment extends PreferenceFragment {
  private static final String TAG = AdvancedPreferenceFragment.class.getSimpleName();

  private static final String PUSH_MESSAGING_PREF   = "pref_toggle_push_messaging";
  private static final String SUBMIT_DEBUG_LOG_PREF = "pref_submit_debug_logs";
  private static final String FORSTA_DASHBOARD_PREFERENCE = "preference_forsta_dashboard";
  private static final String FORSTA_OTR_PREF   = "pref_forsta_otr";
  private static final int PICK_IDENTITY_CONTACT = 1;

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    masterSecret = getArguments().getParcelable("master_secret");
    addPreferencesFromResource(R.xml.preferences_advanced);

    initializeIdentitySelection();

    Preference submitDebugLog = this.findPreference(SUBMIT_DEBUG_LOG_PREF);
    PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("advanced_preferences_screen");
    Preference debugDashboard = this.findPreference(FORSTA_DASHBOARD_PREFERENCE);
    if (!BuildConfig.FORSTA_API_URL.contains("dev")) {
      preferenceScreen.removePreference(debugDashboard);
    } else {
      debugDashboard.setOnPreferenceClickListener(new DashboardClickListener());
    }
    submitDebugLog.setOnPreferenceClickListener(new SubmitDebugLogListener());
    submitDebugLog.setSummary(getVersion(getActivity()));
    initializeOffTheRecordToggle();
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__advanced);
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, Intent data) {
    super.onActivityResult(reqCode, resultCode, data);

    Log.w(TAG, "Got result: " + resultCode + " for req: " + reqCode);
    if (resultCode == Activity.RESULT_OK && reqCode == PICK_IDENTITY_CONTACT) {
      handleIdentitySelection(data);
    }
  }

  private void initializeOffTheRecordToggle() {
    CheckBoxPreference preference = (CheckBoxPreference)this.findPreference(FORSTA_OTR_PREF);
    preference.setEnabled(false);
    if (ForstaPreferences.getOffTheRecord(getActivity())) {
      preference.setChecked(true);
    } else {
      preference.setChecked(false);
    }

    preference.setOnPreferenceChangeListener(new OffTheRecordClickListener());
  }

  private void initializeIdentitySelection() {
    ContactIdentityManager identity = ContactIdentityManager.getInstance(getActivity());

    Preference preference = this.findPreference(TextSecurePreferences.IDENTITY_PREF);

    if (identity.isSelfIdentityAutoDetected()) {
      this.getPreferenceScreen().removePreference(preference);
    } else {
      Uri contactUri = identity.getSelfIdentityUri();

      if (contactUri != null) {
        String contactName = ContactAccessor.getInstance().getNameFromContact(getActivity(), contactUri);
        preference.setSummary(String.format(getString(R.string.ApplicationPreferencesActivity_currently_s),
                                            contactName));
      }

      preference.setOnPreferenceClickListener(new IdentityPreferenceClickListener());
    }
  }

  private @NonNull String getVersion(@Nullable Context context) {
    try {
      if (context == null) return "";

      String app     = context.getString(R.string.app_name);
      String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;

      return String.format("%s %s", app, version);
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, e);
      return context.getString(R.string.app_name);
    }
  }

  private class IdentityPreferenceClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(Intent.ACTION_PICK);
      intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
      startActivityForResult(intent, PICK_IDENTITY_CONTACT);
      return true;
    }
  }

  private void handleIdentitySelection(Intent data) {
    Uri contactUri = data.getData();

    if (contactUri != null) {
      TextSecurePreferences.setIdentityContactUri(getActivity(), contactUri.toString());
      initializeIdentitySelection();
    }
  }

  private class SubmitDebugLogListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final Intent intent = new Intent(getActivity(), ForstaLogSubmitActivity.class);
      startActivity(intent);
      return true;
    }
  }

  private class OffTheRecordClickListener implements Preference.OnPreferenceChangeListener {

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
      boolean value = (boolean) o;
      ForstaPreferences.setOffTheRecord(getActivity(), value);
      initializeOffTheRecordToggle();
      return value;
    }
  }

  private class DashboardClickListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(getActivity(), DashboardActivity.class);
      startActivity(intent);
      return true;
    }
  }
}
