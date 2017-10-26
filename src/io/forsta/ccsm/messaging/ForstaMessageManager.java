package io.forsta.ccsm.messaging;

import android.content.Context;

import java.util.LinkedList;

import io.forsta.ccsm.ThreadPreferenceActivity;
import io.forsta.ccsm.api.model.ForstaControlMessage;
import io.forsta.ccsm.api.model.ForstaMessage;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.mms.OutgoingSecureMediaMessage;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.MessageSender;
import ws.com.google.android.mms.MmsException;

/**
 * Created by jlewis on 10/25/17.
 */

public class ForstaMessageManager {
  private static final String TAG = ForstaMessageManager.class.getSimpleName();

  public static String createMessageBody() {
    return null;
  }

  public static String  createControlMessage() {


    return null;
  }

  public static ForstaMessage fromJsonString() {


    return null;
  }

  public static void sendThreadUpdate(Context context, MasterSecret masterSecret, Recipients recipients, long threadId) {
    try {
      OutgoingMediaMessage message = new OutgoingMediaMessage(recipients, "Control Message", new LinkedList<Attachment>(),  System.currentTimeMillis(), -1, 0, ThreadDatabase.DistributionTypes.DEFAULT);
      message = new OutgoingSecureMediaMessage(message);
      ForstaThread threadData = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadId);
      message.setForstaControlJsonBody(context, threadData);
      MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
      long messageId  = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), message, -1, false);
      MessageSender.sendMediaMessage(context, masterSecret, recipients, false, messageId, 0);
    } catch (MmsException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
