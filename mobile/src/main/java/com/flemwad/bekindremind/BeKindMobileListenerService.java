package com.flemwad.bekindremind;

import android.content.Intent;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Tyler on 4/3/2015.
 */
public class BeKindMobileListenerService extends WearableListenerService {

    private static final String WEAR_PATH = "/be-kind-wear";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        /*
         * Receive the message from wear
         */
        if (messageEvent.getPath().equals(WEAR_PATH)) {
            if (!BeKindMainMobileActivity.active) {
                Intent startIntent = new Intent(this, BeKindMainMobileActivity.class);
                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(startIntent);
            }
        }

    }

}
