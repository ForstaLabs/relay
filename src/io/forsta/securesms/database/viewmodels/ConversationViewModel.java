package io.forsta.securesms.database.viewmodels;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.model.DisplayRecord;
import io.forsta.securesms.database.model.MessageRecord;

public class ConversationViewModel extends AndroidViewModel {
  private static final String TAG = ConversationViewModel.class.getSimpleName();

  private MutableLiveData<List<MessageRecord>> conversationItems;

  public ConversationViewModel(@NonNull Application application) {
    super(application);
  }

  public LiveData<List<MessageRecord>> getConversation(long threadId, long limit) {
    if (conversationItems == null) {
      conversationItems = new MutableLiveData<>();
      MessageRecord record;
      List<MessageRecord> items = new ArrayList<>();
      Cursor cursor = DatabaseFactory.getMmsDatabase(getApplication()).getConversation(threadId, 0);
      MmsDatabase.Reader reader = DatabaseFactory.getMmsDatabase(getApplication()).readerFor(cursor);
      while (reader != null && (record = reader.getNext()) != null) {
        items.add(record);
      }
      conversationItems.setValue(items);

    }
    return conversationItems;
  }


  @Override
  protected void onCleared() {
    Log.i(TAG, "onCleared");
  }
}
