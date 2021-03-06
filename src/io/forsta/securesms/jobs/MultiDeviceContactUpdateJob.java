package io.forsta.securesms.jobs;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.securesms.contacts.ContactAccessor;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.SafeData;
import io.forsta.securesms.jobs.requirements.MasterSecretRequirement;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;

import javax.inject.Inject;

import io.forsta.securesms.dependencies.TextSecureCommunicationModule;

public class MultiDeviceContactUpdateJob extends MasterSecretJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = MultiDeviceContactUpdateJob.class.getSimpleName();
  private static final String KEY_RECIPIENT_ID = "recipient_id";

  @Inject transient TextSecureCommunicationModule.TextSecureMessageSenderFactory messageSenderFactory;

  private long recipientId;

  public MultiDeviceContactUpdateJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public MultiDeviceContactUpdateJob(Context context) {
    this(context, -1);
  }

  public MultiDeviceContactUpdateJob(Context context, long recipientId) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .withMasterSecretRequirement()
                                .withGroupId(MultiDeviceContactUpdateJob.class.getSimpleName())
                                .create());

    this.recipientId = recipientId;
  }

  @Override
  public void onRun(MasterSecret masterSecret)
      throws IOException, UntrustedIdentityException, NetworkException
  {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.w(TAG, "Not multi device, aborting...");
      return;
    }

    if (recipientId <= 0) generateFullContactUpdate();
    else                  generateSingleContactUpdate(recipientId);
  }

  private void generateSingleContactUpdate(long recipientId)
      throws IOException, UntrustedIdentityException, NetworkException
  {
    SignalServiceMessageSender messageSender   = messageSenderFactory.create();
    File                       contactDataFile = createTempFile("multidevice-contact-update");

    try {
      DeviceContactsOutputStream out       = new DeviceContactsOutputStream(new FileOutputStream(contactDataFile));
      Recipient recipient = RecipientFactory.getRecipientForId(context, recipientId, false);

      out.write(new DeviceContact(recipient.getAddress(),
                                  Optional.fromNullable(recipient.getName()),
                                  getAvatar(recipient.getContactUri()),
                                  Optional.fromNullable(recipient.getColor().serialize())));

      out.close();
      sendUpdate(messageSender, contactDataFile);

    } finally {
      if (contactDataFile != null) contactDataFile.delete();
    }
  }

  private void generateFullContactUpdate()
      throws IOException, UntrustedIdentityException, NetworkException
  {
    SignalServiceMessageSender messageSender   = messageSenderFactory.create();
    File                       contactDataFile = createTempFile("multidevice-contact-update");

    try {
      DeviceContactsOutputStream out      = new DeviceContactsOutputStream(new FileOutputStream(contactDataFile));
      Collection<ContactAccessor.ContactData>    contacts = ContactAccessor.getInstance().getContactsWithPush(context);

      for (ContactAccessor.ContactData contactData : contacts) {
        Uri              contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(contactData.id));
        String           number     = contactData.numbers.get(0).number;
        Optional<String> name       = Optional.fromNullable(contactData.name);
        Optional<String> color      = getColor(number);

        out.write(new DeviceContact(number, name, getAvatar(contactUri), color));
      }

      out.close();
      sendUpdate(messageSender, contactDataFile);

    } finally {
      if (contactDataFile != null) contactDataFile.delete();
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onAdded() {

  }

  @NonNull
  @Override
  protected Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putLong(KEY_RECIPIENT_ID, recipientId).build();
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    recipientId = data.getLong(KEY_RECIPIENT_ID);
  }

  @Override
  public void onCanceled() {

  }

  private Optional<String> getColor(String number) {
    if (!TextUtils.isEmpty(number)) {
      Recipients recipients = RecipientFactory.getRecipientsFromString(context, number, false);
      return Optional.of(recipients.getColor().serialize());
    } else {
      return Optional.absent();
    }
  }

  private void sendUpdate(SignalServiceMessageSender messageSender, File contactsFile)
      throws IOException, UntrustedIdentityException, NetworkException
  {
    if (contactsFile.length() > 0) {
      FileInputStream               contactsFileStream = new FileInputStream(contactsFile);
      SignalServiceAttachmentStream attachmentStream   = SignalServiceAttachment.newStreamBuilder()
                                                                                .withStream(contactsFileStream)
                                                                                .withContentType("application/octet-stream")
                                                                                .withLength(contactsFile.length())
                                                                                .build();

      try {
        messageSender.sendMessage(SignalServiceSyncMessage.forContacts(attachmentStream));
      } catch (IOException ioe) {
        throw new NetworkException(ioe);
      }
    }
  }

  private Optional<SignalServiceAttachmentStream> getAvatar(@Nullable Uri uri) throws IOException {
    if (uri == null) {
      return Optional.absent();
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      try {
        Uri                 displayPhotoUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO);
        AssetFileDescriptor fd              = context.getContentResolver().openAssetFileDescriptor(displayPhotoUri, "r");

        return Optional.of(SignalServiceAttachment.newStreamBuilder()
                                                  .withStream(fd.createInputStream())
                                                  .withContentType("image/*")
                                                  .withLength(fd.getLength())
                                                  .build());
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }

    Uri photoUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);

    if (photoUri == null) {
      return Optional.absent();
    }

    Cursor cursor = context.getContentResolver().query(photoUri,
                                                       new String[] {
                                                           ContactsContract.CommonDataKinds.Photo.PHOTO,
                                                           ContactsContract.CommonDataKinds.Phone.MIMETYPE
                                                       }, null, null, null);

    try {
      if (cursor != null && cursor.moveToNext()) {
        byte[] data = cursor.getBlob(0);

        if (data != null) {
          return Optional.of(SignalServiceAttachment.newStreamBuilder()
                                                    .withStream(new ByteArrayInputStream(data))
                                                    .withContentType("image/*")
                                                    .withLength(data.length)
                                                    .build());
        }
      }

      return Optional.absent();
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  private File createTempFile(String prefix) throws IOException {
    File file = File.createTempFile(prefix, "tmp", context.getCacheDir());
    file.deleteOnExit();

    return file;
  }

  private static class NetworkException extends Exception {

    public NetworkException(Exception ioe) {
      super(ioe);
    }
  }

}
