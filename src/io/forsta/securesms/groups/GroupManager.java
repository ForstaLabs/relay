package io.forsta.securesms.groups;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.protobuf.ByteString;

import io.forsta.ccsm.api.ForstaGroup;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.attachments.UriAttachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.AttachmentDatabase;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.mms.OutgoingGroupMediaMessage;
import io.forsta.securesms.providers.SingleUseBlobProvider;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.util.BitmapUtil;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ws.com.google.android.mms.ContentType;

public class GroupManager {
  public static @NonNull GroupActionResult createGroup(@NonNull  Context        context,
                                                       @NonNull MasterSecret masterSecret,
                                                       @NonNull  Set<Recipient> members,
                                                       @Nullable Bitmap         avatar,
                                                       @Nullable String         name)
      throws InvalidNumberException
  {
    final byte[]        avatarBytes       = BitmapUtil.toByteArray(avatar);
    final GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    final byte[]        groupId           = groupDatabase.allocateGroupId();
    final Set<String>   memberE164Numbers = getE164Numbers(context, members);

    memberE164Numbers.add(TextSecurePreferences.getLocalNumber(context));
    groupDatabase.create(groupId, name, new LinkedList<>(memberE164Numbers), null, null);
    groupDatabase.updateAvatar(groupId, avatarBytes);
    return sendGroupUpdate(context, masterSecret, groupId, memberE164Numbers, name, avatarBytes);
  }

  public static @NonNull void createForstaGroup(@NonNull  Context        context,
                                                   @NonNull MasterSecret masterSecret,
                                                   @NonNull  byte[]         groupId,
                                                   @NonNull  Set<Recipient> members,
                                                   @Nullable Bitmap         avatar,
                                                   @Nullable String         name,
                                                   @Nullable String         slug)
          throws InvalidNumberException
  {
    final byte[]        avatarBytes       = BitmapUtil.toByteArray(avatar);
    final GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    final Set<String>   memberE164Numbers = getE164Numbers(context, members);

    groupDatabase.createForstaGroup(groupId, name, slug, new LinkedList<>(memberE164Numbers), null, null);
    groupDatabase.updateAvatar(groupId, avatarBytes);
    // Send message to group members, that you have joined the group.
    // sendGroupUpdate(context, masterSecret, groupId, memberE164Numbers, name, avatarBytes);
  }


  private static Set<String> getE164Numbers(Context context, Collection<Recipient> recipients)
      throws InvalidNumberException
  {
    final Set<String> results = new HashSet<>();
    for (Recipient recipient : recipients) {
      results.add(Util.canonicalizeNumber(context, recipient.getNumber()));
    }

    return results;
  }

  public static void updateForstaGroup(@NonNull  Context        context,
                                          @NonNull  MasterSecret   masterSecret,
                                          @NonNull  byte[]         groupId,
                                          @NonNull  Set<Recipient> members,
                                          @Nullable Bitmap         avatar,
                                          @Nullable String         name,
                                          @Nullable String         slug)
          throws InvalidNumberException
  {

    final GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    final Set<String>   memberE164Numbers = getE164Numbers(context, members);
    final byte[]        avatarBytes       = BitmapUtil.toByteArray(avatar);

    groupDatabase.updateMembers(groupId, new LinkedList<>(memberE164Numbers));
    groupDatabase.updateTitle(groupId, name);
    groupDatabase.updateSlug(groupId, slug);
    groupDatabase.updateAvatar(groupId, avatarBytes);
  }

  public static void removeForstaGroup(Context context, String groupId) {
    GroupDatabase db = DatabaseFactory.getGroupDatabase(context);
    db.removeGroup(groupId);
  }

  public static void removeForstaGroups(Context context, Set<String> groupIds) {
    GroupDatabase db = DatabaseFactory.getGroupDatabase(context);
    db.removeGroups(groupIds);
  }

  public static GroupActionResult updateGroup(@NonNull  Context        context,
                                              @NonNull  MasterSecret   masterSecret,
                                              @NonNull  byte[]         groupId,
                                              @NonNull  Set<Recipient> members,
                                              @Nullable Bitmap         avatar,
                                              @Nullable String         name)
      throws InvalidNumberException
  {
    final GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    final Set<String>   memberE164Numbers = getE164Numbers(context, members);
    final byte[]        avatarBytes       = BitmapUtil.toByteArray(avatar);

    memberE164Numbers.add(TextSecurePreferences.getLocalNumber(context));
    groupDatabase.updateMembers(groupId, new LinkedList<>(memberE164Numbers));
    groupDatabase.updateTitle(groupId, name);
    groupDatabase.updateAvatar(groupId, avatarBytes);

    return sendGroupUpdate(context, masterSecret, groupId, memberE164Numbers, name, avatarBytes);
  }

  private static GroupActionResult sendGroupUpdate(@NonNull  Context      context,
                                                   @NonNull  MasterSecret masterSecret,
                                                   @NonNull  byte[]       groupId,
                                                   @NonNull  Set<String>  e164numbers,
                                                   @Nullable String       groupName,
                                                   @Nullable byte[]       avatar)
  {
    Attachment avatarAttachment = null;
    String     groupRecipientId = GroupUtil.getEncodedId(groupId);
    Recipients groupRecipient   = RecipientFactory.getRecipientsFromString(context, groupRecipientId, false);

    GroupContext.Builder groupContextBuilder = GroupContext.newBuilder()
                                                           .setId(ByteString.copyFrom(groupId))
                                                           .setType(GroupContext.Type.UPDATE)
                                                           .addAllMembers(e164numbers);
    if (groupName != null) groupContextBuilder.setName(groupName);
    GroupContext groupContext = groupContextBuilder.build();

    if (avatar != null) {
      Uri avatarUri = SingleUseBlobProvider.getInstance().createUri(avatar);
      avatarAttachment = new UriAttachment(avatarUri, ContentType.IMAGE_PNG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, avatar.length);
    }

    OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(groupRecipient, groupContext, avatarAttachment, System.currentTimeMillis(), 0);
    long                      threadId        = MessageSender.send(context, masterSecret, outgoingMessage, -1, false);

    return new GroupActionResult(groupRecipient, threadId);
  }

  public static String getGroupIdFromRecipients(Context context, Recipients recipients) {
    GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
    // The order stored in the database appears to be consistent. Can I match the concatenated members string in the db
    // to find an existing group?
    List<String> items = recipients.toNumberStringList(false);
    String localNumber = TextSecurePreferences.getLocalNumber(context);
    String stringItems = "";
    if (!items.contains(localNumber)) {
      stringItems = TextSecurePreferences.getLocalNumber(context) + ",";
    }
    for (int i=0; i<items.size(); i++) {
      stringItems += items.get(i);
      if (i < items.size() -1) {
        stringItems += ",";
      }
    }

    String groupId = groupDatabase.getGroupId(stringItems);
    return groupId;
  }

  public static class GroupActionResult {
    private Recipients groupRecipient;
    private long       threadId;

    public GroupActionResult(Recipients groupRecipient, long threadId) {
      this.groupRecipient = groupRecipient;
      this.threadId       = threadId;
    }

    public Recipients getGroupRecipient() {
      return groupRecipient;
    }

    public long getThreadId() {
      return threadId;
    }
  }
}
