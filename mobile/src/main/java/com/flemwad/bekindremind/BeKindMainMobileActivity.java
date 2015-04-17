package com.flemwad.bekindremind;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

public class BeKindMainMobileActivity extends ActionBarActivity implements MessageApi.MessageListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "PhoneActivity";
    private SharedPreferences beKindSettings;

    public static final String PREFS_NAME = "BeKindRemind";

    public static final String G0_NOTIFICATION_PATH = "/go_notification";
    public static final String GO_DO_VIBRATE = "/go_do_vibrate";

    public static final String GO_NOTIFICATION_TIMESTAMP = "timestamp";
    public static final String GO_NOTIFICATION_TITLE = "title";
    public static final String GO_NOTIFICATION_CONTENT = "content";

    public static final String REMIND_NOTIFICATION_PATH = "/remind_notification";
    public static final String REMIND_BREAK_TIMER = "break_timer";
    public static final String REMIND_COMP_MSG = "comp_msg";
    private static final String REMIND_FINISH = "/remind_finish";

    static boolean active = false;
    static boolean remindTimerRunning = false;

    private GoogleApiClient mGoogleApiClient;

    public int currentReminds = 0;
    public int breakGoal = 4;
    public int breakTimer = 15;
    public int remindInterval = 7200; //30 seconds for conference and debugging
    public int currentRemindInterval = 0;

    public String compMsg = "Feeling Good!";

    //Timer shiz
    Timer remindTimer;
    TimerTask remindTimerTask;

    //we are going to use a handler to be able to run in our TimerTask
    final Handler timerHandler = new Handler();

    Button btnStart;
    Button btnDoneComp;
    Button btnClose;
    Button btnGoalMinus;
    Button btnGoalPlus;
    Button btnTimerMinus;
    Button btnTimerPlus;
    Button btnIntervalMinus;
    Button btnIntervalPlus;

    TextView txtBreakNumber;
    TextView txtBreakTimer;
    TextView txtIntervalNumber;
    TextView txtCurrentProgress;
    TextView txtCountdown;
    EditTextBackEvent etxtCompMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_be_kind_main_mobile);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build();

        //Initialize elements
        btnStart = (Button) findViewById(R.id.btn_start);
        btnDoneComp = (Button) findViewById(R.id.btn_comp_done);
        btnClose = (Button) findViewById(R.id.btn_close);

        btnGoalMinus = (Button) findViewById(R.id.btn_goal_minus);
        btnGoalPlus = (Button) findViewById(R.id.btn_goal_plus);

        btnTimerMinus = (Button) findViewById(R.id.btn_timer_minus);
        btnTimerPlus = (Button) findViewById(R.id.btn_timer_plus);

        btnIntervalMinus = (Button) findViewById(R.id.btn_interval_minus);
        btnIntervalPlus = (Button) findViewById(R.id.btn_interval_plus);

        txtBreakNumber = (TextView) findViewById(R.id.text_break_number);
        txtBreakTimer = (TextView) findViewById(R.id.text_timer_number);
        txtIntervalNumber = (TextView) findViewById(R.id.text_interval_number);

        txtCurrentProgress = (TextView) findViewById(R.id.txt_current_progress);

        txtCountdown = (TextView) findViewById(R.id.text_countdown);

        etxtCompMsg = (EditTextBackEvent) findViewById(R.id.etxt_comp_msg);

        beKindSettings = getSharedPreferences(PREFS_NAME, 0);

        //Grab our values
        getAppValuesFromSettings();

        //Initialize text views
        initTextViews();

        //Init Comp message, for listener on back button
        initEditTextCompMsg();

        //Initialize buttons
        initButtons();

        setCurrentProgressText();

        initTextCountdownValue();
    }

    private void getAppValuesFromSettings () {
        currentReminds = beKindSettings.getInt("currentReminds", 0);
        breakGoal = beKindSettings.getInt("breakGoal", 4);
        breakTimer = beKindSettings.getInt("breakTimer", 15);
        remindInterval = beKindSettings.getInt("remindInterval", 2);

        compMsg = beKindSettings.getString("compMsg", "Feeling Good!");
    }

    private void saveAppValuesFromSettings () {
        SharedPreferences.Editor editor = beKindSettings.edit();
        editor.putInt("currentReminds", currentReminds);
        editor.putInt("breakGoal", breakGoal);
        editor.putInt("breakTimer", breakTimer);
        editor.putInt("remindInterval", remindInterval);
        editor.putString("compMsg", compMsg);

        editor.commit();
    }

    private void initTextViews () {
        txtBreakNumber.setText(String.valueOf(breakGoal));
        txtBreakTimer.setText(String.valueOf(breakTimer));
        txtIntervalNumber.setText(String.valueOf(remindInterval));
        etxtCompMsg.setText(compMsg);

        txtCurrentProgress.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                resetCurrentProgressText();
                return false;
            }
        });
    }

    private void initEditTextCompMsg() {
        etxtCompMsg.setText(compMsg);
        etxtCompMsg.setOnEditTextImeBackListener(new EditTextBackEvent.EditTextImeBackListener() {
            @Override
            public void onImeBack(EditTextBackEvent ctrl, String text) {
                hideSaveCompMessage();
            }
        });
    }

    private void initButtons () {
        btnDoneComp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSaveCompMessage();
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //sendGoNotification();
                if(!remindTimerRunning) startRemindTimer();
            }
        });

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnIntervalMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(remindInterval <= 1 || remindTimerRunning) return;

                remindInterval--;
                initTextCountdownValue();
                //sendTimerUpdateNotification(getFormattedTime(remindInterval));
                txtIntervalNumber.setText(String.valueOf(remindInterval));
            }
        });

        btnIntervalPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //remindInterval >= 4
                if (remindInterval >= 7200 || remindTimerRunning) return; //debug/conference value, replace for prod

                remindInterval++;
                initTextCountdownValue();
                //sendTimerUpdateNotification(getFormattedTime(remindInterval));
                txtIntervalNumber.setText(String.valueOf(remindInterval));
            }
        });

        btnGoalMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (breakGoal <= currentReminds || remindTimerRunning) return;

                breakGoal--;
                txtBreakNumber.setText(String.valueOf(breakGoal));
                setCurrentProgressText();
            }
        });

        btnGoalPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (breakGoal >= 12 || remindTimerRunning) return;

                breakGoal++;
                txtBreakNumber.setText(String.valueOf(breakGoal));
                setCurrentProgressText();
            }
        });

        btnTimerMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(breakTimer <= 0 || remindTimerRunning) return;

                breakTimer--;
                txtBreakTimer.setText(String.valueOf(breakTimer));
            }
        });

        btnTimerPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(breakTimer >= 120 || remindTimerRunning) return;

                breakTimer++;
                txtBreakTimer.setText(String.valueOf(breakTimer));
            }
        });
    }

    private void saveCurrentProgress() {
        SharedPreferences.Editor editor = beKindSettings.edit();
        editor.putInt("currentReminds", currentReminds);

        editor.commit();
    }

    private void resetCurrentProgressText() {
        currentReminds = 0;
        setCurrentProgressText();
        saveCurrentProgress();
    }

    private void setCurrentProgressText() {
        txtCurrentProgress.setText(String.valueOf(currentReminds) + " of " + String.valueOf(breakGoal) + " positive moments");
    }

    private String getCurrentProgressText() {
        return String.valueOf(currentReminds) + " of " + String.valueOf(breakGoal) + " positive moments";
    }

    private void hideSaveCompMessage() {
        //etxtCompMsg = (EditTextBackEvent) findViewById(R.id.etxt_comp_msg);
        compMsg = etxtCompMsg.getText().toString();

        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(etxtCompMsg.getWindowToken(), 0);
    }

    private void remindFinishReset() {
        currentReminds++;

        if (currentReminds >= breakGoal) {
            //win condition
            Toast.makeText(getApplicationContext(), R.string.toast_kind_remind_goal_reached,
                    Toast.LENGTH_LONG).show();
            currentReminds = 0;

            //stop timer and flip running state
        }

        Handler handler = new Handler();
        handler.postDelayed(new Runnable(){
            @Override
            public void run(){
                sendGoNotification();
            }
        }, 1000);

        saveCurrentProgress();
        setCurrentProgressText();
    }

    //Google API Messaging
    @Override
    public void onConnected(Bundle bundle) {
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (messageEvent.getPath().equals(REMIND_FINISH)) {
                    remindFinishReset();

                    Toast.makeText(getApplicationContext(), R.string.toast_kind_remind,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void sendRemindNotification() {
        if (mGoogleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(REMIND_NOTIFICATION_PATH);
            // Make sure the data item is unique. Usually, this will not be required, as the payload
            // (in this case the title and the content of the notification) will be different for almost all
            // situations. However, in this example, the text and the content are always the same, so we need
            // to disambiguate the data item by adding a field that contains teh current time in milliseconds.
            dataMapRequest.getDataMap().putDouble(GO_NOTIFICATION_TIMESTAMP, System.currentTimeMillis());
            dataMapRequest.getDataMap().putInt(REMIND_BREAK_TIMER, breakTimer);
            dataMapRequest.getDataMap().putString(REMIND_COMP_MSG, compMsg);
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
        }
        else {
            Log.e(TAG, "No connection to wearable available!");
        }
    }
    
    private void sendGoNotification() {
        if (mGoogleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(G0_NOTIFICATION_PATH);
            // Make sure the data item is unique. Usually, this will not be required, as the payload
            // (in this case the title and the content of the notification) will be different for almost all
            // situations. However, in this example, the text and the content are always the same, so we need
            // to disambiguate the data item by adding a field that contains teh current time in milliseconds.
            dataMapRequest.getDataMap().putDouble(GO_NOTIFICATION_TIMESTAMP, System.currentTimeMillis());
            dataMapRequest.getDataMap().putString(GO_NOTIFICATION_TITLE, getCurrentProgressText());
            dataMapRequest.getDataMap().putString(GO_NOTIFICATION_CONTENT, "--:--:--"); //getFormattedTime(remindInterval)
            dataMapRequest.getDataMap().putBoolean(GO_DO_VIBRATE, true);
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
        }
        else {
            Log.e(TAG, "No connection to wearable available!");
        }
    }

    private void sendTimerUpdateNotification(String _currentRemindInterval) {
        if (mGoogleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(G0_NOTIFICATION_PATH);
            // Make sure the data item is unique. Usually, this will not be required, as the payload
            // (in this case the title and the content of the notification) will be different for almost all
            // situations. However, in this example, the text and the content are always the same, so we need
            // to disambiguate the data item by adding a field that contains teh current time in milliseconds.
            dataMapRequest.getDataMap().putDouble(GO_NOTIFICATION_TIMESTAMP, System.currentTimeMillis());
            dataMapRequest.getDataMap().putString(GO_NOTIFICATION_TITLE, getCurrentProgressText());
            dataMapRequest.getDataMap().putString(GO_NOTIFICATION_CONTENT, _currentRemindInterval);
            dataMapRequest.getDataMap().putBoolean(GO_DO_VIBRATE, false);
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
        }
        else {
            Log.e(TAG, "No connection to wearable available!");
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "Failed to connect to Google Api Client with error code "
                + connectionResult.getErrorCode());
    }
    //End Google API Messaging

    //Activity Overrides
    @Override
    protected void onPause() {
        super.onPause();

        saveAppValuesFromSettings();
        active = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        active = true;
    }

    @Override
    protected void onDestroy() {
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }

        active = false;
        saveAppValuesFromSettings();
        super.onDestroy();
    }
    //end Activity Overrides

    //Header Menu overrides (Want to get rid of these)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_be_kind_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    //end Header Menu overrides

    //Timer
    private void resetCurrentRemindInterval() {
        currentRemindInterval = remindInterval;
        stopRemindTimerTask();
    }

    private void startRemindTimer() {
        //set a new Timer
        remindTimer = new Timer();

        //initialize the TimerTask's job
        initializeTimerTask();

        //schedule the timer, after the first 0ms the TimerTask will run every 10000ms
        //Tick every second to update the view, can wrap this with less interruptions later
        //Or... change the update value 10 seconds before their meditation
        remindTimer.schedule(remindTimerTask, 0, 1000); //
    }

    private void updateTextCountdownValue(String time) {
        txtCountdown.setText(time);
    }

    private void initTextCountdownValue() {
        updateTextCountdownValue(getFormattedTime(remindInterval));
    }

    private void stopRemindTimerTask() {
        //stop the timer, if it's not already null
        if (remindTimer != null) {
            remindTimerRunning = false;
            remindTimer.cancel();
            remindTimer = null;
        }
    }

    private String getFormattedTime(int seconds) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        final SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
        df.setTimeZone(tz);
        return df.format(new Date(seconds * 1000));
    }

    private void initializeTimerTask() {
        currentRemindInterval = remindInterval;

        remindTimerTask = new TimerTask() {
            public void run() {

                //use a handler to run a toast that shows the current timestamp
                timerHandler.post(new Runnable() {
                    public void run() {
                        remindTimerRunning = true;
                        currentRemindInterval--;
                        String currTime = getFormattedTime(currentRemindInterval);

                        if (currentRemindInterval <= 0) {
                            //Done push notification and reset timer
                            resetCurrentRemindInterval();
                            initTextCountdownValue();
                            sendRemindNotification();
                            return;
                        }
                        else if(currentRemindInterval == 7199) {
                            currTime = "02:00:00";
                            sendTimerUpdateNotification(currTime);
                        }
                        else if(currentRemindInterval == 5400) { //1 hour 30 min left
                            sendTimerUpdateNotification(currTime);
                        }
                        else if(currentRemindInterval == 3600) { //1 hour left
                            sendTimerUpdateNotification(currTime);
                        }
                        else if (currentRemindInterval == 2700) { //45 min left
                            sendTimerUpdateNotification(currTime);
                        }
                        else if(currentRemindInterval == 1800) { //30 min left
                            sendTimerUpdateNotification(currTime);
                        }
                        else if(currentRemindInterval == 900) { //15 min left
                            sendTimerUpdateNotification(currTime);
                        }
                        else if(currentRemindInterval == 298) { //5 min left
                            sendTimerUpdateNotification(currTime);
                        }

                        if(currentRemindInterval <= 30) { //count down every second after 30 sec
                            sendTimerUpdateNotification(currTime);
                        }

                        //set mobile view values off seconds conversion
                        updateTextCountdownValue(currTime);
                    }
                });
            }
        };
    }
    //End Timer

}
