package io.forsta.securesms.components;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.Timer;
import java.util.TimerTask;

import io.forsta.securesms.MessageDetailsActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.util.ServiceUtil;
import io.forsta.securesms.util.ViewUtil;

public class ContactFilterToolbar extends Toolbar {
  private static final String TAG = ContactFilterToolbar.class.getSimpleName();
  private   OnFilterChangedListener listener;
  private   OnClickListener searchListener;
  private   OnClickListener createConversationListener;

  private EditText        searchText;
  private AnimatingToggle toggle;
  private ImageView       action;
  private ImageView       keyboardToggle;
  private ImageView       dialpadToggle;
  private ImageView       clearToggle;
  private ImageView       createConversationToggle;
  private LinearLayout    toggleContainer;
  private ImageView       searchToggle;
  private long debounceThreshhold = 500L;
  private Timer debounceTimer;
  private final int DEBOUNCE_MESSAGE = 13;
  private Handler handler;

  public ContactFilterToolbar(Context context) {
    this(context, null);
  }

  public ContactFilterToolbar(Context context, AttributeSet attrs) {
    this(context, attrs, R.attr.toolbarStyle);
  }

  public ContactFilterToolbar(final Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    handler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
        super.handleMessage(msg);
        if (msg.what == DEBOUNCE_MESSAGE) {
          notifyListener();
        }
      }
    };
    inflate(context, R.layout.contact_filter_toolbar, this);

    this.action          = ViewUtil.findById(this, R.id.action_icon);
    this.searchText      = ViewUtil.findById(this, R.id.search_view);
    this.toggle          = ViewUtil.findById(this, R.id.button_toggle);
    this.keyboardToggle  = ViewUtil.findById(this, R.id.search_keyboard);
    this.dialpadToggle   = ViewUtil.findById(this, R.id.search_dialpad);
    this.clearToggle     = ViewUtil.findById(this, R.id.search_clear);
    this.toggleContainer = ViewUtil.findById(this, R.id.toggle_container);
    this.searchToggle = ViewUtil.findById(this, R.id.toolbar_search_directory);
    this.createConversationToggle = ViewUtil.findById(this, R.id.toolbar_create_conversation);
    this.clearToggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        searchText.setText("");
      }
    });

    this.searchText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        Log.w(TAG, "Text input: " + s.toString());
        if (debounceTimer != null) {
          Log.w(TAG, "Cancelling timer...");
          debounceTimer.cancel();
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
        debounceTimer = new Timer();
        Log.w(TAG, "Scheduling update");
        debounceTimer.schedule(new TimerTask() {
          @Override
          public void run() {
            Log.w(TAG, "Updating...");
            if (handler != null) {
              handler.sendEmptyMessage(DEBOUNCE_MESSAGE);
            }
          }
        }, debounceThreshhold);
      }
    });

    expandTapArea(this, action);
    expandTapArea(toggleContainer, dialpadToggle);
    displayTogglingView(searchToggle);
  }

  @Override
  public void setNavigationIcon(int resId) {
    action.setImageResource(resId);
  }

  @Override
  public void setNavigationOnClickListener(OnClickListener listener) {
    super.setNavigationOnClickListener(listener);
    action.setOnClickListener(listener);
  }

  public void setShowCustomNavigationButton(boolean show) {
    action.setVisibility(show ? VISIBLE : GONE);
  }

  public void clear() {
    searchText.setText("");
    notifyListener();
  }

  public String getSearchText() {
    return searchText.getText().toString();
  }

  public void setSearchText(String text) {
    searchText.setText(text);
    searchText.setSelection(searchText.length());
  }

  public void setOnFilterChangedListener(OnFilterChangedListener listener) {
    this.listener = listener;
  }

  public void setSearchOnClickListener(OnClickListener listener) {
    this.searchToggle.setOnClickListener(listener);
  }

  public void setCreateConversationListener(OnClickListener listener) {
    this.createConversationToggle.setOnClickListener(listener);
  }

  private void notifyListener() {
    if (listener != null) listener.onFilterChanged(searchText.getText().toString());
  }

  private void displayTogglingView(View view) {
    toggle.display(view);
    expandTapArea(toggleContainer, view);
  }

  public void setToolbarHintSms() {
    searchText.setHint(R.string.contact_selection_activity__enter_name_or_number_sms);
  }

  public void showCreateConversationToggle() {
    displayTogglingView(createConversationToggle);
  }

  public void updateToggleState(boolean hasSelected, boolean hasResults) {
    if (searchText.length() < 1) {
      if (hasSelected) {
        displayTogglingView(createConversationToggle);
      } else {
        displayTogglingView(searchToggle);
      }
    } else {
      if (hasResults) {
        displayTogglingView(clearToggle);
      } else {
        displayTogglingView(searchToggle);
      }
    }
  }

  public void showSearchToggle() {
    displayTogglingView(searchToggle);
  }

  private void expandTapArea(final View container, final View child) {
    final int padding = getResources().getDimensionPixelSize(R.dimen.contact_selection_actions_tap_area);

    container.post(new Runnable() {
      @Override
      public void run() {
        Rect rect = new Rect();
        child.getHitRect(rect);

        rect.top -= padding;
        rect.left -= padding;
        rect.right += padding;
        rect.bottom += padding;

        container.setTouchDelegate(new TouchDelegate(rect, child));
      }
    });
  }

  private static class SearchUtil {
    public static boolean isTextInput(EditText editText) {
      return (editText.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT;
    }

    public static boolean isPhoneInput(EditText editText) {
      return (editText.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE;
    }

    public static boolean isEmpty(EditText editText) {
      return editText.getText().length() <= 0;
    }
  }

  public interface OnFilterChangedListener {
    void onFilterChanged(String filter);
  }
}
