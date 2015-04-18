package com.flemwad.bekindremind;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.wearable.activity.ConfirmationActivity;
import android.util.Log;
import android.os.Vibrator;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import static com.google.android.gms.wearable.PutDataRequest.WEAR_URI_SCHEME;

public class BeKindWearNotificationService extends WearableListenerService {

    //data points
    public static final String G0_NOTIFICATION_PATH = "/go_notification";
    public static final String GO_DO_VIBRATE = "/go_do_vibrate";
    public static final String REMIND_NOTIFICATION_PATH = "/remind_notification";
    private static final String REMIND_FINISH_CONFIRM = "/remind_finish_confirm";

    public static final String GO_NOTIFICATION_TITLE = "title";
    public static final String GO_NOTIFICATION_CONTENT = "content";
    public static final String REMIND_BREAK_TIMER = "break_timer";
    public static final String REMIND_COMP_MSG = "comp_msg";
    public static final String OPEN_PHONE_INTENT_ACTION = "com.flemwad.bekindremind.receiver.action.OPENPHONE";

    public static final String ACTION_DISMISS = "com.flemwad.notificationwithopenactivityonwearableaction.DISMISS";

    private int notificationId = 001;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (null != intent) {
            String action = intent.getAction();
            if (ACTION_DISMISS.equals(action)) {
                //dismissNotification();
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for(DataEvent dataEvent: dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                if (G0_NOTIFICATION_PATH.equals(dataEvent.getDataItem().getUri().getPath())) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataEvent.getDataItem());
                    String title = dataMapItem.getDataMap().getString(GO_NOTIFICATION_TITLE);
                    String content = dataMapItem.getDataMap().getString(GO_NOTIFICATION_CONTENT);
                    boolean doVibrate = dataMapItem.getDataMap().getBoolean(GO_DO_VIBRATE);
                    sendGoNotification(content, title, doVibrate);
                }
                else if (REMIND_NOTIFICATION_PATH.equals(dataEvent.getDataItem().getUri().getPath())) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataEvent.getDataItem());

                    int breakTimer = dataMapItem.getDataMap().getInt(REMIND_BREAK_TIMER);
                    String compMsg = dataMapItem.getDataMap().getString(REMIND_COMP_MSG);

                    try {
                        sendRemindConfirmationIntent(breakTimer, compMsg);
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                }
                else if(REMIND_FINISH_CONFIRM.equals(dataEvent.getDataItem().getUri().getPath())) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataEvent.getDataItem());

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

    private void sendGoNotification(String title, String content, boolean doVibrate) {
        Intent intent = new Intent(OPEN_PHONE_INTENT_ACTION);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingViewIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // this intent will be sent when the user swipes the notification to dismiss it
        Intent dismissIntent = new Intent(ACTION_DISMISS);
        PendingIntent pendingDeleteIntent = PendingIntent.getService(this, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(Notification.PRIORITY_MAX)
                .setDeleteIntent(pendingDeleteIntent)
                .addAction(R.drawable.go_to_phone_00156,
                        getString(R.string.openphone), pendingViewIntent);
                //.setContentIntent(pendingViewIntent);

        Notification notification = builder.build();

        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
        notificationManagerCompat.notify(notificationId, notification);

        if(doVibrate) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(500);
        }
    }

    private void dismissNotification() {
        new DismissNotificationCommand(this).execute();
    }

    private class DismissNotificationCommand implements GoogleApiClient.ConnectionCallbacks, ResultCallback<DataApi.DeleteDataItemsResult>, GoogleApiClient.OnConnectionFailedListener{

        private static final String TAG = "DismissNotification";

        private final GoogleApiClient mGoogleApiClient;

        public DismissNotificationCommand(Context context) {
            mGoogleApiClient = new GoogleApiClient.Builder(context)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }

        public void execute() {
            mGoogleApiClient.connect();
        }

        @Override
        public void onConnected(Bundle bundle) {
            final Uri dataGoItemUri = new Uri.Builder().scheme(WEAR_URI_SCHEME).path(G0_NOTIFICATION_PATH).build();
            final Uri dataRemindItemUri = new Uri.Builder().scheme(WEAR_URI_SCHEME).path(REMIND_NOTIFICATION_PATH).build();
            final Uri dataConfirmUri = new Uri.Builder().scheme(WEAR_URI_SCHEME).path(REMIND_FINISH_CONFIRM).build();


//            if (Log.isLoggable(TAG, Log.DEBUG)) {
//                Log.d(TAG, "Deleting Uri: " + dataItemUri.toString());
//            }
            Wearable.DataApi.deleteDataItems(mGoogleApiClient, dataGoItemUri).setResultCallback(this);
            Wearable.DataApi.deleteDataItems(mGoogleApiClient, dataRemindItemUri).setResultCallback(this);
            Wearable.DataApi.deleteDataItems(mGoogleApiClient, dataConfirmUri).setResultCallback(this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "onConnectionSuspended");
        }

        @Override
        public void onResult(DataApi.DeleteDataItemsResult deleteDataItemsResult) {
            if (!deleteDataItemsResult.getStatus().isSuccess()) {
                Log.e(TAG, "dismissWearableNotification(): failed to delete DataItem");
            }
            mGoogleApiClient.disconnect();
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d(TAG, "onConnectionFailed");
        }
    }
}
