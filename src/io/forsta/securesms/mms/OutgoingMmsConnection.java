package io.forsta.securesms.mms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.forsta.securesms.transport.UndeliverableMessageException;

import ws.com.google.android.mms.pdu.SendConf;

public interface OutgoingMmsConnection {
  @Nullable SendConf send(@NonNull byte[] pduBytes, int subscriptionId) throws UndeliverableMessageException;
}
