package com.positizing.watch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;

/**
 * PositizingNotificationReceiver:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 08/09/2024 @ 10:27 p.m.
 */
class PositizingNotificationReceiver extends BroadcastReceiver {

    private final WatchNotificationActivity watchNotificationActivity;

    public PositizingNotificationReceiver(final WatchNotificationActivity watchNotificationActivity) {
        this.watchNotificationActivity = watchNotificationActivity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String temp = intent.getStringExtra("notification_event") + "\n" + watchNotificationActivity.textView.getText();
        final TextView text = watchNotificationActivity.textView;
        final String oldText = text.getText().toString();
        if (oldText.isEmpty()) {
            text.setText(temp);
        } else {
            text.setText(oldText + "\n" + temp);
        }
        watchNotificationActivity.notifyUser(temp);
    }
}
