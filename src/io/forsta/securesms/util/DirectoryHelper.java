package io.forsta.securesms.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.os.Build;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.ForstaSyncAdapter;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.R;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.SessionUtil;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.NotInDirectoryException;
import io.forsta.securesms.database.TextSecureDirectory;
import io.forsta.securesms.jobs.MultiDeviceContactUpdateJob;
import io.forsta.securesms.notifications.MessageNotifier;
import io.forsta.securesms.push.TextSecureCommunicationFactory;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.IncomingJoinedMessage;
import io.forsta.securesms.util.DirectoryHelper.UserCapabilities.Capability;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DirectoryHelper {

  public static class UserCapabilities {

    public static final UserCapabilities UNKNOWN     = new UserCapabilities(Capability.UNKNOWN, Capability.UNKNOWN);
    public static final UserCapabilities UNSUPPORTED = new UserCapabilities(Capability.UNSUPPORTED, Capability.UNSUPPORTED);

    public enum Capability {
      UNKNOWN, SUPPORTED, UNSUPPORTED
    }

    private final Capability text;
    private final Capability voice;

    public UserCapabilities(Capability text, Capability voice) {
      this.text  = text;
      this.voice = voice;
    }

    public Capability getTextCapability() {
      return text;
    }

    public Capability getVoiceCapability() {
      return voice;
    }
  }

  private static final String TAG = DirectoryHelper.class.getSimpleName();

  public static void refreshDirectory(@NonNull Context context, @Nullable MasterSecret masterSecret)
      throws IOException
  {
    CcsmApi.syncForstaContacts(context);
    List<String> newUsers = refreshDirectory(context,
                                             TextSecureCommunicationFactory.createManager(context),
                                             TextSecurePreferences.getLocalNumber(context));

    if (!newUsers.isEmpty() && TextSecurePreferences.isMultiDevice(context)) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceContactUpdateJob(context));
    }

//    notifyNewUsers(context, masterSecret, newUsers);
    context.sendBroadcast(new Intent(ForstaSyncAdapter.FORSTA_SYNC_COMPLETE));
  }

  public static @NonNull List<String> refreshDirectory(@NonNull Context context,
                                                       @NonNull ForstaServiceAccountManager accountManager,
                                                       @NonNull String localNumber)
      throws IOException
  {
    ContactDb contactsDb = DbFactory.getContactDb(context);
    Set<String> eligibleContactAddresses = contactsDb.getAddresses();
    eligibleContactAddresses.add(TextUtils.isEmpty(localNumber) ? TextSecurePreferences.getLocalNumber(context) : localNumber);
    eligibleContactAddresses.add(BuildConfig.FORSTA_SYNC_NUMBER);

    TextSecureDirectory directory = TextSecureDirectory.getInstance(context);
    List<ContactTokenDetails> activeTokens = accountManager.getContacts(eligibleContactAddresses);

    if (activeTokens != null) {
      for (ContactTokenDetails activeToken : activeTokens) {
        eligibleContactAddresses.remove(activeToken.getNumber());
        activeToken.setNumber(activeToken.getNumber()); //Huh?
      }

      directory.setNumbers(activeTokens, eligibleContactAddresses);
      // Update the forsta contacts db to set active users.
      contactsDb.setActiveForstaAddresses(activeTokens, eligibleContactAddresses);
      contactsDb.close();
    }

    return new LinkedList<>();
  }

  // Obsolete
  public static UserCapabilities refreshDirectoryFor(@NonNull  Context context,
                                                     @Nullable MasterSecret masterSecret,
                                                     @NonNull  Recipients recipients,
                                                     @NonNull  String localNumber)
      throws IOException
  {
    try {
      TextSecureDirectory           directory      = TextSecureDirectory.getInstance(context);
      ForstaServiceAccountManager   accountManager = TextSecureCommunicationFactory.createManager(context);
      String                        number         = Util.canonicalizeNumber(context, recipients.getPrimaryRecipient().getNumber());
      Optional<ContactTokenDetails> details        = accountManager.getContact(number);

      if (details.isPresent()) {
        directory.setNumber(details.get(), true);

        List<ContactTokenDetails> activeTokens = new ArrayList<>();
        activeTokens.add(details.get());
        ContactDb contactsDb = DbFactory.getContactDb(context);
        ForstaUser user = contactsDb.getUserByAddress(number);
        if (user == null) {
          // We do not recognize this user. Try to refresh from CCSM.
          CcsmApi.syncForstaContacts(context);
        }
        contactsDb.setActiveForstaAddresses(activeTokens);

        return new UserCapabilities(Capability.SUPPORTED, details.get().isVoice() ? Capability.SUPPORTED : Capability.UNSUPPORTED);
      } else {
        ContactTokenDetails absent = new ContactTokenDetails();
        absent.setNumber(number);
        directory.setNumber(absent, false);
        return UserCapabilities.UNSUPPORTED;
      }
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return UserCapabilities.UNSUPPORTED;
    }
  }

  public static UserCapabilities refreshDirectoryFor(@NonNull Context context, @Nullable MasterSecret masterSecret, @NonNull Recipients recipients) {
    TextSecureDirectory           directory      = TextSecureDirectory.getInstance(context);
    ForstaServiceAccountManager   accountManager = TextSecureCommunicationFactory.createManager(context);

    try {
      if (recipients.isGroupRecipient()) {
        Optional<ContactTokenDetails> details = accountManager.getContact(recipients.getPrimaryRecipient().getNumber());
        if (details.isPresent()) {
          directory.setNumber(details.get(), true);
          // Now go get unknown tag from CCSM
          return new UserCapabilities(Capability.SUPPORTED, Capability.UNSUPPORTED);
        }
      }
      List<String> addresses = recipients.toNumberStringList(false);
      String ids = TextUtils.join(",", addresses);

      List<ContactTokenDetails> details = accountManager.getContacts(new HashSet<String>(addresses));
      if (details.size() == 0) {
        return new UserCapabilities(Capability.UNKNOWN, Capability.UNKNOWN);
      }
      directory.setNumbers(details, new ArrayList<String>());
      CcsmApi.syncForstaContacts(context, ids);
      ContactDb contactsDb = DbFactory.getContactDb(context);
      contactsDb.setActiveForstaAddresses(details);
      RecipientFactory.clearCache(context);
      return new UserCapabilities(Capability.SUPPORTED, Capability.UNSUPPORTED);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static @NonNull UserCapabilities getUserCapabilities(@NonNull Context context,
                                                              @Nullable Recipients recipients)
  {
    try {
      if (recipients == null) {
        return UserCapabilities.UNSUPPORTED;
      }

      if (!TextSecurePreferences.isPushRegistered(context)) {
        return UserCapabilities.UNSUPPORTED;
      }

      if (!recipients.isSingleRecipient()) {
        boolean isSecure = false;
        for (Recipient recipient : recipients) {
          isSecure  = TextSecureDirectory.getInstance(context).isSecureTextSupported(recipient.getNumber());
        }
        if (isSecure) {
          return new UserCapabilities(Capability.SUPPORTED, Capability.UNSUPPORTED);
        }
        return UserCapabilities.UNSUPPORTED;
      }

      if (recipients.isGroupRecipient()) {
        return new UserCapabilities(Capability.SUPPORTED, Capability.UNSUPPORTED);
      }

      final String number = recipients.getPrimaryRecipient().getNumber();

      if (number == null) {
        return UserCapabilities.UNSUPPORTED;
      }

      String  e164number  = Util.canonicalizeNumber(context, number);
      boolean secureText  = TextSecureDirectory.getInstance(context).isSecureTextSupported(e164number);
      boolean secureVoice = TextSecureDirectory.getInstance(context).isSecureVoiceSupported(e164number);

      return new UserCapabilities(secureText  ? Capability.SUPPORTED : Capability.UNSUPPORTED,
                                  secureVoice ? Capability.SUPPORTED : Capability.UNSUPPORTED);

    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return UserCapabilities.UNSUPPORTED;
    } catch (NotInDirectoryException e) {
      return UserCapabilities.UNKNOWN;
    }
  }

  private static @NonNull List<String> updateContactsDatabase(@NonNull Context context,
                                                              @NonNull String localNumber,
                                                              @NonNull final ContactTokenDetails activeToken)
  {
    return updateContactsDatabase(context, localNumber,
                                  new LinkedList<ContactTokenDetails>() {{add(activeToken);}},
                                  false);
  }

  private static @NonNull List<String> updateContactsDatabase(@NonNull Context context,
                                                              @NonNull String localNumber,
                                                              @NonNull List<ContactTokenDetails> activeTokens,
                                                              boolean removeMissing)
  {
    Optional<Account> account = getOrCreateAccount(context);

    if (account.isPresent()) {
      try {
        return  DatabaseFactory.getContactsDatabase(context)
                               .setRegisteredUsers(account.get(), localNumber, activeTokens, removeMissing);
      } catch (RemoteException | OperationApplicationException e) {
        Log.w(TAG, e);
      }
    }

    return new LinkedList<>();
  }

  private static void notifyNewUsers(@NonNull  Context context,
                                     @Nullable MasterSecret masterSecret,
                                     @NonNull  List<String> newUsers)
  {
    if (!TextSecurePreferences.isNewContactsNotificationEnabled(context)) return;

    for (String newUser : newUsers) {
      if (!SessionUtil.hasSession(context, masterSecret, newUser) && !Util.isOwnNumber(context, newUser)) {
        IncomingJoinedMessage message        = new IncomingJoinedMessage(newUser);
        Pair<Long, Long>      smsAndThreadId = DatabaseFactory.getSmsDatabase(context).insertMessageInbox(message);

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 9 && hour < 23) {
          MessageNotifier.updateNotification(context, masterSecret, false, smsAndThreadId.second, true);
        } else {
          MessageNotifier.updateNotification(context, masterSecret, false, smsAndThreadId.second, false);
        }
      }
    }
  }

  public static Optional<Account> getOrCreateAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account[]      accounts       = accountManager.getAccountsByType(BuildConfig.APPLICATION_ID);

    Optional<Account> account;

    if (accounts.length == 0) account = createAccount(context);
    else                      account = Optional.of(accounts[0]);

    if (account.isPresent() && !ContentResolver.getSyncAutomatically(account.get(), ContactsContract.AUTHORITY)) {
      ContentResolver.setSyncAutomatically(account.get(), ContactsContract.AUTHORITY, true);
    }

    return account;
  }

  private static Optional<Account> createAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account        account        = new Account(context.getString(R.string.app_name), BuildConfig.APPLICATION_ID);

    if (accountManager.addAccountExplicitly(account, null, null)) {
      Log.w(TAG, "Created new account...");
      ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
      return Optional.of(account);
    } else {
      Log.w(TAG, "Failed to create account!");
      return Optional.absent();
    }
  }
}
