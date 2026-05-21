package com.deviceveil.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MonitorEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        MonitorEventStore.record(context, intent);
    }
}
