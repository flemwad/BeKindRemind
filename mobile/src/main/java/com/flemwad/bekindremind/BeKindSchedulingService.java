package com.flemwad.bekindremind;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Tyler on 4/21/2015.
 */
public class BeKindSchedulingService extends IntentService {

    public static final String TAG = "Be Kind Scheduler";
    // An ID used to post the notification.
    public static final int NOTIFICATION_ID = 2;
    public static final String OPEN_PHONE_INTENT_ACTION = "com.flemwad.bekindremind.receiver.action.OPENPHONE";

    private static boolean isRunning = false;

    public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_ALARM_GET = 3;

    private List<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all current registered clients.

    private final Messenger mMessenger = new Messenger(new IncomingMessageHandler()); // Target we publish for clients to send messages to IncomingHandler.

    private NotificationManager mNotificationManager;

    public BeKindSchedulingService() {
        super("BeKindSchedulingService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service Started.");
        isRunning = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent,flags,startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        //wake the activity up so we may grab our data, if it's not already running
        if (!BeKindMainMobileActivity.active) {
            Intent startIntent = new Intent(this, BeKindMainMobileActivity.class);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startIntent);
        }

        //send out a request to BeKindMainMobileActivity to get the contents of the notification here
        //instead of passing them through the original alarm where they can't be dynamic
        sendGetMessageToUI();

        // END_INCLUDE(service_onhandle)
    }

    public static boolean isRunning()
    {
        return isRunning;
    }

    private void sendGoNotification(String title, String content, boolean doVibrate) {
        Intent intent = new Intent(OPEN_PHONE_INTENT_ACTION);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingViewIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // this intent will be sent when the user swipes the notification to dismiss it
        //Intent dismissIntent = new Intent(ACTION_DISMISS);
        //PendingIntent pendingDeleteIntent = PendingIntent.getService(this, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Action action =
                new NotificationCompat.Action.Builder(R.drawable.go_to_phone_00156,
                        getString(R.string.openphone), pendingViewIntent)
                        .build();

        NotificationCompat.WearableExtender wearableExtender =
                new NotificationCompat.WearableExtender();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(pendingViewIntent);
                //.extend(new NotificationCompat.WearableExtender().addAction(action))


        mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_ID, builder.build());

        WakefulBroadcastReceiver.completeWakefulIntent(intent);
    }

    private void sendGetMessageToUI() {
        Iterator<Messenger> messengerIterator = mClients.iterator();
        while(messengerIterator.hasNext()) {
            Messenger messenger = messengerIterator.next();
            try {
                // Send request for alarm datas
                messenger.send(Message.obtain(null, MSG_ALARM_GET));
            } catch (RemoteException e) {
                // The client is dead. Remove it from the list.
                mClients.remove(messenger);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return mMessenger.getBinder();
    }

    private class IncomingMessageHandler extends Handler { // Handler of incoming messages from clients.
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage: " + msg.what);
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                case MSG_ALARM_GET:
                    Hashtable<String, String> messages = (Hashtable<String, String>)msg.obj;
                    sendGoNotification(messages.get("title"), messages.get("content"), true);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
