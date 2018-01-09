package io.forsta.securesms.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.ForstaSyncAdapter;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.R;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.SessionUtil;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.NotInDirectoryException;
import io.forsta.securesms.database.TextSecureDirectory;
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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DirectoryHelper {
  private static final String TAG = DirectoryHelper.class.getSimpleName();

  public static void resetDirectory(Context context) throws IOException {
    refreshDirectory(context, TextSecureCommunicationFactory.createManager(context), TextSecurePreferences.getLocalNumber(context), true);
  }

  public static void refreshDirectory(@NonNull Context context, @Nullable MasterSecret masterSecret) throws IOException {
    refreshDirectory(context, TextSecureCommunicationFactory.createManager(context), TextSecurePreferences.getLocalNumber(context), false);
  }

  public static void refreshDirectory(@NonNull Context context, @NonNull ForstaServiceAccountManager accountManager, @NonNull String localNumber) throws IOException {
    refreshDirectory(context, accountManager, localNumber, false);
  }

  public static void refreshDirectory(@NonNull Context context, @NonNull ForstaServiceAccountManager accountManager, @NonNull String localNumber, boolean resetDirectory) throws IOException {
    CcsmApi.syncForstaContacts(context, resetDirectory);
    ContactDb contactsDb = DbFactory.getContactDb(context);
    Set<String> eligibleContactAddresses = contactsDb.getAddresses();
    // Two devices had crashes because of a null UID value in the contactsDb
    if (eligibleContactAddresses.contains(null)) {
      Log.e(TAG, "Null number in contactsDb!");
      eligibleContactAddresses.remove(null);
    }

    eligibleContactAddresses.add(localNumber);
    TextSecureDirectory directory = TextSecureDirectory.getInstance(context);
    List<ContactTokenDetails> activeTokens = accountManager.getContacts(eligibleContactAddresses);

    if (activeTokens != null) {
      for (ContactTokenDetails activeToken : activeTokens) {
        eligibleContactAddresses.remove(activeToken.getNumber());
      }

      directory.setNumbers(activeTokens, eligibleContactAddresses);
      contactsDb.setActiveForstaAddresses(activeTokens, eligibleContactAddresses);
    }
    notifyRefresh(context);
  }

  public static void refreshDirectoryFor(@NonNull Context context, @Nullable MasterSecret masterSecret, @NonNull Recipients recipients) {
    TextSecureDirectory           directory      = TextSecureDirectory.getInstance(context);
    ForstaServiceAccountManager   accountManager = TextSecureCommunicationFactory.createManager(context);
    ContactDb contactsDb = DbFactory.getContactDb(context);

    try {
      List<String> addresses = recipients.toNumberStringList(false);
      if (addresses.size() > 0) {
        CcsmApi.syncForstaContacts(context, addresses);
        List<ContactTokenDetails> details = accountManager.getContacts(new HashSet<>(addresses));
        if (details.size() > 0) {
          directory.setNumbers(details, new ArrayList<String>());
          contactsDb.setActiveForstaAddresses(details);
          notifyRefresh(context);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void refreshDirectoryFor(Context context, MasterSecret masterSecret, List<String> addresses) {
    TextSecureDirectory           directory      = TextSecureDirectory.getInstance(context);
    ForstaServiceAccountManager   accountManager = TextSecureCommunicationFactory.createManager(context);

    try {
      if (addresses.size() > 0) {
        CcsmApi.syncForstaContacts(context, addresses);
        List<ContactTokenDetails> details = accountManager.getContacts(new HashSet<String>(addresses));
        if (details.size() > 0) {
          directory.setNumbers(details, new ArrayList<String>());
          ContactDb contactsDb = DbFactory.getContactDb(context);
          contactsDb.setActiveForstaAddresses(details);
          notifyRefresh(context);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void notifyRefresh(Context context) {
    RecipientFactory.clearCache(context);
    context.sendBroadcast(new Intent(ForstaSyncAdapter.FORSTA_SYNC_COMPLETE));
  }

  private static void refreshRecipients(Context context, List<String> addresses) {
    RecipientFactory.getRecipientsFromStrings(context, new ArrayList<String>(addresses), false);
  }

  public static @NonNull UserCapabilities getUserCapabilities(@NonNull Context context, @Nullable Recipients recipients) {
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
          isSecure  = TextSecureDirectory.getInstance(context).isSecureTextSupported(recipient.getAddress());
        }
        if (isSecure) {
          return new UserCapabilities(Capability.SUPPORTED, Capability.UNSUPPORTED);
        }
        return UserCapabilities.UNKNOWN;
      }

      if (recipients.isGroupRecipient()) {
        return new UserCapabilities(Capability.SUPPORTED, Capability.UNSUPPORTED);
      }

      final String number = recipients.getPrimaryRecipient().getAddress();

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
}
