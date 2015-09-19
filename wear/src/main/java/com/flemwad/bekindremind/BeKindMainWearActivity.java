package com.flemwad.bekindremind;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.wearable.activity.ConfirmationActivity;
import android.support.wearable.view.DelayedConfirmationView;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.w3c.dom.Text;

public class BeKindMainWearActivity extends Activity implements DelayedConfirmationView.DelayedConfirmationListener, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "BeKindMainWearActivity";
    public static final String GO_NOTIFICATION_TIMESTAMP = "timestamp";
    private static final String REMIND_FINISH = "/remind_finish";
    private static final String REMIND_FINISH_CONFIRM = "/remind_finish_confirm";
    public static final String REMIND_COMP_MSG = "comp_msg";

    private DelayedConfirmationView mDelayedViewRemind;
    private TextView txtViewBreakTimer;
    private GoogleApiClient mGoogleApiClient;

    private int breakTimer = 0;
    private String compMsg = "";
    static boolean active = false;
    private PowerManager.WakeLock mWakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_be_kind_main_wear);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK |PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE ), "MyWakelockTag");
        mWakeLock.acquire();

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mDelayedViewRemind = (DelayedConfirmationView) findViewById(R.id.delayed_remind_confirm);
                txtViewBreakTimer = (TextView) findViewById(R.id.text_break_timer);
                txtViewBreakTimer.setText("... Enjoy a nice " + Integer.toString(breakTimer) + " sec break ...");
                showRemindConfirmation();
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addOnConnectionFailedListener(this)
                .build();

    }

    //Activity Overrides
    @Override
    protected void onStart() {
        super.onStart();
        active = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        active = false;
    }

    @Override
    public void onPause() {
        super.onPause();  // Always call the superclass method first
    }

    @Override
    public void onResume() {
        super.onResume();  // Always call the superclass method first

        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        Intent callingIntent = getIntent();
        Bundle extras = callingIntent.getExtras();

        if(extras != null) {
            breakTimer = extras.getInt("breakTimer");
            compMsg = extras.getString("compMsg");
        }

    }

    @Override
    protected void onDestroy() {
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }

        if(mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        super.onDestroy();
    }
    //End Activity Overrides

    //Delay Confirm Timer
    private void showRemindConfirmation () {
        if(breakTimer != 0) {
            mDelayedViewRemind.setTotalTimeMs(breakTimer * 1000);
            mDelayedViewRemind.start();

            mDelayedViewRemind.setListener(this);
        }
    }

    private void sendRemindFinishConfirm() {
        //To show the confirmation checkbox with compMsg from service
        //If we call it from this context, the finish on my timer will short circuit it
        //and it looks like crap then
        if (mGoogleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(REMIND_FINISH_CONFIRM);

            dataMapRequest.getDataMap().putDouble(GO_NOTIFICATION_TIMESTAMP, System.currentTimeMillis());
            dataMapRequest.getDataMap().putString(REMIND_COMP_MSG, compMsg);

            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
        }
        else {
            Log.e(TAG, "No connection to wearable available!");
        }
    }

    @Override
    public void onTimerFinished(View view) {
        // User didn't cancel, perform the action
        sendMessageToCompanion(REMIND_FINISH);

        sendRemindFinishConfirm();

        finish();
    }

    @Override
    public void onTimerSelected(View view) {
        // User canceled, abort the action
        view.setPressed(false);
        return;
    }
    //End Delay Confirm Timer Overrides

    //Message helpers
    private void sendMessageToCompanion(final String path) {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(
                new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                        for (final Node node : getConnectedNodesResult.getNodes()) {
                            Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), path,
                                    new byte[0]).setResultCallback(getSendMessageResultCallback());
                        }
                    }
                }
        );

    }

    private ResultCallback<MessageApi.SendMessageResult> getSendMessageResultCallback() {
        return new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                if (!sendMessageResult.getStatus().isSuccess()) {
                    Log.e(TAG, "Failed to connect to Google Api Client with status "
                            + sendMessageResult.getStatus());
                }
            }
        };
    }
    //End message helpers

    //Google Api Client Conntection Failed Override
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "Failed to connect to Google Api Client");
    }
}
