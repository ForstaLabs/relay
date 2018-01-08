package io.forsta.securesms.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.forsta.ccsm.database.model.ForstaTag;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.BitmapUtil;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GroupDatabase extends Database {
  private static final String TAG = GroupDatabase.class.getSimpleName();
  public static final String DATABASE_UPDATE_ACTION = "GroupDatabase.UPDATE";

  private static final String TABLE_NAME          = "groups";
  public static final String ID                  = "_id";
  public static final String GROUP_ID            = "group_id";
  public static final String TITLE               = "title";
  public static final String MEMBERS             = "members";
  private static final String AVATAR              = "avatar";
  private static final String AVATAR_ID           = "avatar_id";
  private static final String AVATAR_KEY          = "avatar_key";
  private static final String AVATAR_CONTENT_TYPE = "avatar_content_type";
  private static final String AVATAR_RELAY        = "avatar_relay";
  public static final String TIMESTAMP           = "timestamp";
  public static final String ORG_ID              = "org_id";
  public static final String ORG_SLUG = "org_slug";
  public static final String SLUG                = "slug";
  private static final String SLUG_IDS            = "slug_ids";
  public static final String GROUP_DISTRIBUTION  = "group_distribution";
  private static final String ACTIVE              = "active";

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME +
          " (" + ID + " INTEGER PRIMARY KEY, " +
          GROUP_ID + " TEXT, " +
          TITLE + " TEXT, " +
          MEMBERS + " TEXT, " +
          AVATAR + " BLOB, " +
          AVATAR_ID + " INTEGER, " +
          AVATAR_KEY + " BLOB, " +
          AVATAR_CONTENT_TYPE + " TEXT, " +
          AVATAR_RELAY + " TEXT, " +
          TIMESTAMP + " INTEGER, " +
          ORG_ID + " TEXT, " +
          ORG_SLUG + " TEXT, " +
          SLUG + " TEXT, " +
          SLUG_IDS + " TEXT, " +
          GROUP_DISTRIBUTION + " INTEGER DEFAULT 0, " +
          ACTIVE + " INTEGER DEFAULT 1);";

  public static final String[] CREATE_INDEXS = {
      "CREATE UNIQUE INDEX IF NOT EXISTS group_id_index ON " + TABLE_NAME + " (" + GROUP_ID + ");",
  };

  public GroupDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public @Nullable GroupRecord getGroup(String endcodedGroupId) {
    @SuppressLint("Recycle")
    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, GROUP_ID + " = ?",
        new String[] {endcodedGroupId},
        null, null, null);

    Reader      reader = new Reader(cursor);
    GroupRecord record = reader.getNext();

    reader.close();
    return record;
  }

  public @Nullable GroupRecord getGroup(byte[] groupId) {
    @SuppressLint("Recycle")
    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, GROUP_ID + " = ?",
                                                               new String[] {GroupUtil.getEncodedId(groupId)},
                                                               null, null, null);

    Reader      reader = new Reader(cursor);
    GroupRecord record = reader.getNext();

    reader.close();
    return record;
  }

  public Reader getGroupsFilteredByTitle(String constraint) {
    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, TITLE + " LIKE ?",
                                                               new String[]{"%" + constraint + "%"},
                                                               null, null, null);

    return new Reader(cursor);
  }

  public Reader getGroups() {
    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null);
    return new Reader(cursor);
  }

  public Cursor getForstaGroups(String slugPart) {
    String selection = GROUP_DISTRIBUTION + " = 0";
    String[] selectionValues = null;
    if (slugPart != null && slugPart.length() > 0) {
      selection += " AND " + SLUG + " LIKE ?";
      selectionValues = new String[] { "%" + slugPart + "%"};
    }
    return databaseHelper.getReadableDatabase().query(TABLE_NAME, null, selection, selectionValues, null, null, null);
  }

  public Cursor getForstaGroupsByTitle(String nameFilter) {
    String selection = GROUP_DISTRIBUTION + " = 0";
    String[] selectionValues = null;
    if (nameFilter != null && nameFilter.length() > 0) {
      selection += " AND " + TITLE + " LIKE ?";
      selectionValues = new String[] { "%" + nameFilter + "%" };
    }
    return databaseHelper.getReadableDatabase().query(TABLE_NAME, null, selection, selectionValues, null, null, null);
  }

  public @NonNull
  Recipients getGroupMembers(byte[] groupId, boolean includeSelf) {
    String          localNumber = TextSecurePreferences.getLocalNumber(context);
    List<String>    members     = getCurrentMembers(groupId);
    List<Recipient> recipients  = new LinkedList<>();

    for (String member : members) {
      if (!includeSelf && member.equals(localNumber))
        continue;

      recipients.addAll(RecipientFactory.getRecipientsFromString(context, member, false)
                                        .getRecipientsList());
    }

    return RecipientFactory.getRecipientsFor(context, recipients, false);
  }

  public void updateGroups(List<ForstaTag> groups, List<String> activeNumbers) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    Set<String> groupIds = new HashSet<>();
    // Don't get locally created groups.
    Cursor cursor = db.query(TABLE_NAME, new String[]{GROUP_ID}, SLUG + " IS NOT NULL", null, null, null, null);
    while (cursor != null && cursor.moveToNext()) {
      String groupId = cursor.getString(cursor.getColumnIndex(GROUP_ID));
      groupIds.add(groupId);
    }
    cursor.close();

    db.beginTransaction();
    try {
      for (ForstaTag group : groups) {
        String id = group.getEncodedId();
        List<String> members = new ArrayList<>(group.members);
        for (int i=0; i<members.size(); i++) {
          if (!activeNumbers.contains(members.get(i))) {
            members.remove(i);
          }
        }
        Collections.sort(members);
        String thisNumber = TextSecurePreferences.getLocalNumber(context);

        if (!(members.size() == 1 && members.contains(thisNumber))) {
          ContentValues contentValues = new ContentValues();
          contentValues.put(TITLE, group.description);
          contentValues.put(SLUG, group.slug);
          contentValues.put(ORG_ID, group.org_id);
          contentValues.put(ORG_SLUG, group.org_slug);
          contentValues.put(MEMBERS, Util.join(members, ","));
          contentValues.put(TIMESTAMP, System.currentTimeMillis());
          contentValues.put(ACTIVE, 1);
          if (!groupIds.contains(id)) {
            contentValues.put(GROUP_ID, id);
            db.insert(TABLE_NAME, null, contentValues);
          } else {
            db.update(TABLE_NAME, contentValues, GROUP_ID + "=?", new String[] {id});
          }
          // Remove this id from the set and delete remaining from local db.
          groupIds.remove(id);
        }
      }
      db.setTransactionSuccessful();
      notifyDatabaseListeners();
    }
    finally {
      db.endTransaction();
    }

    db.beginTransaction();
    try {
      // Now remove entries that are no longer valid.
      for (String groupId : groupIds) {
        db.delete(TABLE_NAME, GROUP_ID + "=?", new String[] {groupId});
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void createForstaGroup(byte[] groupId, String title, List<String> members,
                     SignalServiceAttachmentPointer avatar, String relay)
  {
    // Sort the list so that we can find a group based on the member list stored in table.
    Collections.sort(members);
    ContentValues contentValues = new ContentValues();
    contentValues.put(GROUP_ID, GroupUtil.getEncodedId(groupId));
    contentValues.put(TITLE, title);
    contentValues.put(MEMBERS, Util.join(members, ","));

    if (avatar != null) {
      contentValues.put(AVATAR_ID, avatar.getId());
      contentValues.put(AVATAR_KEY, avatar.getKey());
      contentValues.put(AVATAR_CONTENT_TYPE, avatar.getContentType());
    }

    contentValues.put(AVATAR_RELAY, relay);
    contentValues.put(TIMESTAMP, System.currentTimeMillis());
    contentValues.put(GROUP_DISTRIBUTION, 1);
    contentValues.put(ACTIVE, 1);

    databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
    notifyDatabaseListeners();
  }

  public String getGroupId(String members) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor = db.query(TABLE_NAME, null, MEMBERS + "=?", new String[] {members}, null, null, null);
    String id = "";
    if (cursor != null && cursor.moveToNext()) {
      id = cursor.getString(cursor.getColumnIndex(GROUP_ID));
    }
    cursor.close();
    return id;
  }

  public void create(byte[] groupId, String title, List<String> members,
                     SignalServiceAttachmentPointer avatar, String relay)
  {
    // Sort the list so that we can find a group based on the member list stored in table.
    List<String> modifiableMembers = new ArrayList<String>(members);
    Collections.sort(modifiableMembers);
    ContentValues contentValues = new ContentValues();
    contentValues.put(GROUP_ID, GroupUtil.getEncodedId(groupId));
    contentValues.put(TITLE, title);
    contentValues.put(MEMBERS, Util.join(modifiableMembers, ","));

    if (avatar != null) {
      contentValues.put(AVATAR_ID, avatar.getId());
      contentValues.put(AVATAR_KEY, avatar.getKey());
      contentValues.put(AVATAR_CONTENT_TYPE, avatar.getContentType());
    }

    contentValues.put(AVATAR_RELAY, relay);
    contentValues.put(TIMESTAMP, System.currentTimeMillis());
    // TODO need a mechanism to recognize when a group is a distribution and not a tag or other group.
    if (title.contains("@") && title.contains("+")) {
      contentValues.put(GROUP_DISTRIBUTION, 1);
    }
    contentValues.put(ACTIVE, 1);

    databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
  }

  public void update(byte[] groupId, String title, SignalServiceAttachmentPointer avatar) {
    ContentValues contentValues = new ContentValues();
    if (title != null) contentValues.put(TITLE, title);

    if (avatar != null) {
      contentValues.put(AVATAR_ID, avatar.getId());
      contentValues.put(AVATAR_CONTENT_TYPE, avatar.getContentType());
      contentValues.put(AVATAR_KEY, avatar.getKey());
    }

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues,
                                                GROUP_ID + " = ?",
                                                new String[] {GroupUtil.getEncodedId(groupId)});

    RecipientFactory.clearCache(context);
    notifyDatabaseListeners();
  }

  public void updateSlug(byte[] groupId, String slug) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(SLUG, slug);
    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, GROUP_ID +  " = ?",
        new String[] {GroupUtil.getEncodedId(groupId)});

    RecipientFactory.clearCache(context);
    notifyDatabaseListeners();
  }

  public void updateTitle(byte[] groupId, String title) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(TITLE, title);
    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, GROUP_ID +  " = ?",
                                                new String[] {GroupUtil.getEncodedId(groupId)});

    RecipientFactory.clearCache(context);
    notifyDatabaseListeners();
  }

  public void updateAvatar(byte[] groupId, Bitmap avatar) {
    updateAvatar(groupId, BitmapUtil.toByteArray(avatar));
  }

  public void updateAvatar(byte[] groupId, byte[] avatar) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(AVATAR, avatar);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, GROUP_ID +  " = ?",
                                                new String[] {GroupUtil.getEncodedId(groupId)});

    RecipientFactory.clearCache(context);
    notifyDatabaseListeners();
  }

  public void updateMembers(byte[] id, List<String> members) {
    ContentValues contents = new ContentValues();
    contents.put(MEMBERS, Util.join(members, ","));
    contents.put(ACTIVE, 1);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?",
                                                new String[] {GroupUtil.getEncodedId(id)});
  }

  public void remove(byte[] id, String source) {
    List<String> currentMembers = getCurrentMembers(id);
    currentMembers.remove(source);

    ContentValues contents = new ContentValues();
    contents.put(MEMBERS, Util.join(currentMembers, ","));

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?",
                                                new String[] {GroupUtil.getEncodedId(id)});
  }

  public void removeGroup(String id) {
    databaseHelper.getWritableDatabase().delete(TABLE_NAME, GROUP_ID + "=?", new String[] {id});
  }

  public void removeGroups(Set<String> groupIds) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();
    try {

      for (String id : groupIds) {
        // Don't delete locally created groups.
        db.delete(TABLE_NAME, GROUP_ID + "=? AND " + SLUG + " IS NOT NULL", new String[] {id});
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void removeAllGroups() {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }
  private List<String> getCurrentMembers(byte[] id) {
    Cursor cursor = null;

    try {
      cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[] {MEMBERS},
                                                          GROUP_ID + " = ?",
                                                          new String[] {GroupUtil.getEncodedId(id)},
                                                          null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return Util.split(cursor.getString(cursor.getColumnIndexOrThrow(MEMBERS)), ",");
      }

      return new LinkedList<>();
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public boolean isActive(byte[] id) {
    GroupRecord record = getGroup(id);
    return record != null && record.isActive();
  }

  public void setActive(byte[] id, boolean active) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues();
    values.put(ACTIVE, active ? 1 : 0);
    database.update(TABLE_NAME, values, GROUP_ID + " = ?", new String[] {GroupUtil.getEncodedId(id)});
  }

  public byte[] allocateGroupId() {
    UUID uid = UUID.randomUUID();
    byte[] groupId = uid.toString().getBytes();
    return groupId;
  }

  private void notifyDatabaseListeners() {
    Intent intent = new Intent(DATABASE_UPDATE_ACTION);
    context.sendBroadcast(intent);
  }

  public static class Reader {

    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public @Nullable GroupRecord getNext() {
      if (cursor == null || !cursor.moveToNext()) {
        return null;
      }

      return new GroupRecord(cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID)),
                             cursor.getString(cursor.getColumnIndexOrThrow(TITLE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(MEMBERS)),
                             cursor.getBlob(cursor.getColumnIndexOrThrow(AVATAR)),
                             cursor.getLong(cursor.getColumnIndexOrThrow(AVATAR_ID)),
                             cursor.getBlob(cursor.getColumnIndexOrThrow(AVATAR_KEY)),
                             cursor.getString(cursor.getColumnIndexOrThrow(AVATAR_CONTENT_TYPE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(AVATAR_RELAY)),
                             cursor.getInt(cursor.getColumnIndexOrThrow(ACTIVE)) == 1,
                             cursor.getString(cursor.getColumnIndexOrThrow(SLUG)),
                             cursor.getString(cursor.getColumnIndexOrThrow(ORG_SLUG))
      );
    }

    public void close() {
      if (this.cursor != null)
        this.cursor.close();
    }
  }

  public static class GroupRecord {

    private final String       id;
    private final String       title;
    private final List<String> members;
    private final byte[]       avatar;
    private final long         avatarId;
    private final byte[]       avatarKey;
    private final String       avatarContentType;
    private final String       relay;
    private final boolean      active;
    private final String slug;
    private final String org_slug;

    public GroupRecord(String id, String title, String members, byte[] avatar,
                       long avatarId, byte[] avatarKey, String avatarContentType,
                       String relay, boolean active, String slug, String org_slug)
    {
      this.id                = id;
      this.title             = title;
      this.members           = Util.split(members, ",");
      this.avatar            = avatar;
      this.avatarId          = avatarId;
      this.avatarKey         = avatarKey;
      this.avatarContentType = avatarContentType;
      this.relay             = relay;
      this.active            = active;
      this.slug = slug;
      this.org_slug = org_slug;
    }

    public byte[] getId() {
      try {
        return GroupUtil.getDecodedId(id);
      } catch (IOException ioe) {
        throw new AssertionError(ioe);
      }
    }

    public String getEncodedId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public List<String> getMembers() {
      return members;
    }

    public byte[] getAvatar() {
      return avatar;
    }

    public long getAvatarId() {
      return avatarId;
    }

    public byte[] getAvatarKey() {
      return avatarKey;
    }

    public String getAvatarContentType() {
      return avatarContentType;
    }

    public String getRelay() {
      return relay;
    }

    public boolean isActive() {
      return active;
    }

    public String getTag() {
      return slug;
    }

    public String getOrgTag() {
      return org_slug;
    }

    public String getFullTag() {
      return new StringBuilder().append(slug).append(":").append(org_slug).toString();
    }

    public String getFormattedTag(String currentOrg) {
      return "@" + (currentOrg.equals(org_slug) ? getTag() : getFullTag());
    }

    public String getExpression(ForstaUser localUser) {
      StringBuilder sb = new StringBuilder();
      List<String> members = getMembers();
      if (!members.contains(localUser.uid)) {
        sb.append(localUser.getFullTag()).append(" ");
      }
      sb.append(getFullTag());
      return sb.toString();
    }
  }
}
