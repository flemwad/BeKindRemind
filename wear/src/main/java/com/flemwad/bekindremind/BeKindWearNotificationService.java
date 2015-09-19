package com.flemwad.bekindremind;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.wearable.activity.ConfirmationActivity;
import android.os.Vibrator;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

public class BeKindWearNotificationService extends WearableListenerService {

    //data points
    public static final String REMIND_NOTIFICATION_PATH = "/remind_notification";
    private static final String REMIND_FINISH_CONFIRM = "/remind_finish_confirm";
    private static final String ALARM_GET = "/alarm_get";

    public static final String REMIND_BREAK_TIMER = "break_timer";
    public static final String REMIND_COMP_MSG = "comp_msg";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataEvent.getDataItem());

                if (REMIND_NOTIFICATION_PATH.equals(dataEvent.getDataItem().getUri().getPath())) {
                    int breakTimer = dataMapItem.getDataMap().getInt(REMIND_BREAK_TIMER);
                    String compMsg = dataMapItem.getDataMap().getString(REMIND_COMP_MSG);

                    try {
                        sendRemindConfirmationIntent(breakTimer, compMsg);
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                }
                else if (ALARM_GET.equals(dataEvent.getDataItem().getUri().getPath())) {

                }
                else if (REMIND_FINISH_CONFIRM.equals(dataEvent.getDataItem().getUri().getPath())) {
                    String compMsg = dataMapItem.getDataMap().getString(REMIND_COMP_MSG);
                    sendFinishConfirmationMessage(compMsg);
                }
            }
        }
    }

    private void sendRemindConfirmationIntent(int breakTimer, String compMsg) throws PendingIntent.CanceledException {
        Intent intent = new Intent(this, BeKindMainWearActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.putExtra("breakTimer", breakTimer);
        intent.putExtra("compMsg", compMsg);

        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(250);

        startActivity(intent);
    }

    private void sendFinishConfirmationMessage(String compMsg) {
        Intent intent = new Intent(this, ConfirmationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                ConfirmationActivity.SUCCESS_ANIMATION);
        intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE,
                compMsg);
        startActivity(intent);
    }
}
