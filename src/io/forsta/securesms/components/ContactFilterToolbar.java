package io.forsta.securesms.components;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import io.forsta.securesms.R;
import io.forsta.securesms.util.ServiceUtil;
import io.forsta.securesms.util.ViewUtil;

public class ContactFilterToolbar extends Toolbar {
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

  public ContactFilterToolbar(Context context) {
    this(context, null);
  }

  public ContactFilterToolbar(Context context, AttributeSet attrs) {
    this(context, attrs, R.attr.toolbarStyle);
  }

  public ContactFilterToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
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

//    this.keyboardToggle.setOnClickListener(new View.OnClickListener() {
//      @Override
//      public void onClick(View v) {
//        searchText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
//        ServiceUtil.getInputMethodManager(getContext()).showSoftInput(searchText, 0);
//        displayTogglingView(dialpadToggle);
//      }
//    });
//
//    this.dialpadToggle.setOnClickListener(new View.OnClickListener() {
//      @Override
//      public void onClick(View v) {
//        searchText.setInputType(InputType.TYPE_CLASS_PHONE);
//        ServiceUtil.getInputMethodManager(getContext()).showSoftInput(searchText, 0);
//        displayTogglingView(keyboardToggle);
//      }
//    });

    this.clearToggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        searchText.setText("");

//        if (SearchUtil.isTextInput(searchText)) displayTogglingView(dialpadToggle);
//        else displayTogglingView(keyboardToggle);
      }
    });

    this.searchText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {
//        if (SearchUtil.isEmpty(searchText)) {
//          displayTogglingView(Toggle);
//        }
//        else if (SearchUtil.isTextInput(searchText)) displayTogglingView(dialpadToggle);
//        else if (SearchUtil.isPhoneInput(searchText)) displayTogglingView(keyboardToggle);
        notifyListener();
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
