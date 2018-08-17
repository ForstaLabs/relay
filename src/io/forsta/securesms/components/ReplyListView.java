package io.forsta.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

public class ReplyListView extends ListView {
    public ReplyListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ReplyListView(Context context) {
        super(context);
    }

    public ReplyListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2,
                MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, expandSpec);
    }
}
