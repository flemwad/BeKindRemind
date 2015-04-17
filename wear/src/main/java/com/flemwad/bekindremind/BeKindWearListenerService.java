package com.flemwad.bekindremind;

import android.content.Intent;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Tyler on 4/9/2015.
 */
public class BeKindWearListenerService extends WearableListenerService {
    private static final String REMIND_NOTIFICATION_PATH = "/remind_notification";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        /*
         * Receive the message from wear
         */
        if (messageEvent.getPath().equals(REMIND_NOTIFICATION_PATH)) {
                Intent startIntent = new Intent(this, BeKindMainWearActivity.class);
                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(startIntent);
        }

    }
}
