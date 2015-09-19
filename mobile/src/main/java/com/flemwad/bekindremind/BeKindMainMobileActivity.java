package com.flemwad.bekindremind;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
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

import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

public class BeKindMainMobileActivity extends ActionBarActivity implements MessageApi.MessageListener, ServiceConnection,
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

    private static final String ALARM_GET = "/alarm_get";

    static boolean active = false;
    static boolean remindTimerRunning = false;
    public boolean remindAlarmRunning = false;

    //Explicity for communicated to watch Service, not regular IntentServices ;)
    private GoogleApiClient mGoogleApiClient;

    public int currentReminds = 0;
    public int breakGoal = 4;
    public int breakTimer = 15;
    public int remindInterval = 15; //30 seconds for conference and debugging
    public int currentRemindInterval = 0;
    public String compMsg = "Feeling Good!";

    //All timer and date related shiz
    DateTimeFormatter dateTimeParser = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss Z");
    public DateTime resumeRemindDateTime;
    public DateTime startRemindDateTime;
    public int stopTimeElapsed = 0;
    public int totalStoppageTimeInSeconds = 0;
    public DateTime startPauseDateTime;



    //Timer shiz
    Timer remindTimer;
    TimerTask remindTimerTask;

    //Alarm and Schedule Service Messenger Stuffs
    BeKindAlarmReceiver remindAlarm = new BeKindAlarmReceiver();
    private final Messenger mMessenger = new Messenger(new IncomingMessageHandler());
    private ServiceConnection mConnection = this;
    private Messenger mServiceMessenger = null;
    boolean mIsBound;

    //we are going to use a handler to be able to run in our TimerTask
    final Handler timerHandler = new Handler();
    Runnable runnableTimer;
    Runnable runnableRemindFinish;

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

        //Grab our values, and also resume Timer if it was already running
        getAppValuesFromSettings();

        //Initialize text views
        initTextViews();

        //Init Comp message, for listener on back button
        initEditTextCompMsg();

        //Initialize buttons
        initButtons();

        setCurrentProgressText();

        automaticBind();
    }

    private void getAppValuesFromSettings () {
        currentReminds = beKindSettings.getInt("currentReminds", 0);
        breakGoal = beKindSettings.getInt("breakGoal", 4);
        breakTimer = beKindSettings.getInt("breakTimer", 15);
        remindInterval = beKindSettings.getInt("remindInterval", 15); //change this back to realistic interval

        compMsg = beKindSettings.getString("compMsg", "Feeling Good!");

        remindTimerRunning = beKindSettings.getBoolean("remindTimerRunning", false);
        remindAlarmRunning = beKindSettings.getBoolean("remindAlarmRunning", false);

        totalStoppageTimeInSeconds = beKindSettings.getInt("totalStoppageTimeInSeconds", 0);

        resumeTimerAfterGet();
    }

    private void saveAppValuesFromSettings () {
        SharedPreferences.Editor editor = beKindSettings.edit();
        editor.putInt("currentReminds", currentReminds);
        editor.putInt("breakGoal", breakGoal);
        editor.putInt("breakTimer", breakTimer);
        editor.putInt("remindInterval", remindInterval);
        editor.putString("compMsg", compMsg);
        editor.putBoolean("remindTimerRunning", remindTimerRunning);
        editor.putBoolean("remindAlarmRunning", remindAlarmRunning);

        editor.commit();
    }

    private void resetAllValues() {
        SharedPreferences.Editor editor = beKindSettings.edit();
        editor.clear();
        editor.commit();

        currentReminds = 0;
        breakGoal = 4;
        breakTimer = 15;
        remindInterval = 15; //30 seconds for conference and debugging
        currentRemindInterval = 0;

        stopTimeElapsed = 0;
        totalStoppageTimeInSeconds = 0;

        remindAlarmRunning = false;
        remindTimerRunning = false;

        compMsg = "Feeling Good!";

        initTextViews();
        setCurrentProgressText();
        initTextCountdownValue();
        initEditTextCompMsg();

        Toast.makeText(getApplicationContext(), "Reset complete.", Toast.LENGTH_SHORT).show();
    }

    private void resumeTimerAfterGet() {
        int timeElapsedInSeconds;
        //this will change depending if the timer was in a "stopped" or "started" state when the app closed
        if (remindTimerRunning && remindAlarmRunning) {
            timeElapsedInSeconds = calculateResumeTime(true);

            if(timeElapsedInSeconds < remindInterval) {
                //we have to adjust this with the "paused" time because it's calculated from when the timer was originally started
                currentRemindInterval = (remindInterval - timeElapsedInSeconds) + totalStoppageTimeInSeconds;

                resumeRemindTimer();
            }
            else {
                currentRemindInterval = 0;
                remindTimerRunning = false;
                remindAlarmRunning = false;
                resetCurrentRemindInterval();
            }
        }
        else {
            timeElapsedInSeconds = calculateResumeTime(false);

            if(timeElapsedInSeconds < remindInterval) {
                currentRemindInterval = remindInterval - timeElapsedInSeconds;
                updateTextCountdownValue(getFormattedTime(currentRemindInterval));
            }
            else {
                currentRemindInterval = 0;
                initTextCountdownValue();
            }
        }
    }

    private int calculateResumeTime(boolean isRunning) {
        String theTime;
        //Get right now's time to compare difference.
        resumeRemindDateTime = DateTime.now();

        if(isRunning) {
            theTime = beKindSettings.getString("startRemindDateTime", "");
            startRemindDateTime = theTime == "" ? null : dateTimeParser.parseDateTime(theTime);

            return startRemindDateTime == null ? remindInterval : Seconds.secondsBetween(startRemindDateTime, resumeRemindDateTime).getSeconds();
        }
        else {
            theTime = beKindSettings.getString("startPauseDateTime", "");
            stopTimeElapsed = beKindSettings.getInt("stopTimeElapsed", 0);

            //this is for keeping track of how long the user has paused, during this iteration
            startPauseDateTime = theTime == "" ? null : dateTimeParser.parseDateTime(theTime);

            stopTimeElapsed = beKindSettings.getInt("stopTimeElapsed", 0);
            return stopTimeElapsed == 0 ? remindInterval : stopTimeElapsed;
        }

        //Toast.makeText(getApplicationContext(), Integer.toString(seconds), Toast.LENGTH_LONG).show();
    }

    private void initTextViews () {
        txtBreakNumber.setText(String.valueOf(breakGoal));
        txtBreakTimer.setText(String.valueOf(breakTimer));
        txtIntervalNumber.setText(String.valueOf(remindInterval));
        etxtCompMsg.setText(compMsg);

        txtCurrentProgress.setOnLongClickListener(null);
        txtCurrentProgress.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                resetCurrentProgressText();
                return false;
            }
        });

        txtCountdown.setOnLongClickListener(null);
        txtCountdown.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                if (!remindAlarmRunning && !remindTimerRunning) {
                    resetCurrentRemindInterval();
                    return false;
                } else {
                    Toast.makeText(getApplicationContext(), "Stop the timer before resetting.", Toast.LENGTH_SHORT).show();
                }

                return true;
            }
        });

        if(remindAlarmRunning && remindTimerRunning) {
            btnStart.setText("STOP");
        }
        else {
            btnStart.setText("GO");
        }
    }

    private void initEditTextCompMsg() {
        etxtCompMsg.setText(compMsg);

        etxtCompMsg.setOnEditTextImeBackListener(null);
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
                //sendNotification("Flem Test");
                hideSaveCompMessage();
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (remindAlarmRunning && remindTimerRunning) {
                    stopAlarm();
                    stopRemindTimerTask();
                    btnStart.setText("GO");
                } else {
                    startAlarm();
                    startRemindTimer();
                    btnStart.setText("STOP");
                }
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
                if (remindInterval <= 1 || remindTimerRunning) return;

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
                if (remindInterval >= 7200 || remindTimerRunning)
                    return; //debug/conference value, replace for prod

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
                if (breakTimer <= 0 || remindTimerRunning) return;

                breakTimer--;
                txtBreakTimer.setText(String.valueOf(breakTimer));
            }
        });

        btnTimerPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (breakTimer >= 120 || remindTimerRunning) return;

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

    private void saveStartRemindDateTime() {
        SharedPreferences.Editor editor = beKindSettings.edit();

        editor.putString("startRemindDateTime", dateTimeParser.print(startRemindDateTime));
        //make the stop time the reset time
        editor.remove("stopTimeElapsed");

        editor.commit();
    }

    private void savePauseRemindDateTime() {
        SharedPreferences.Editor editor = beKindSettings.edit();

        //this is for keeping track of how long the user has paused, during this iteration
        startPauseDateTime = DateTime.now();
        stopTimeElapsed = remindInterval - currentRemindInterval;
        editor.putString("startPauseDateTime", dateTimeParser.print(startPauseDateTime));
        editor.putInt("stopTimeElapsed", stopTimeElapsed);
        //make the start time the reset time
        //editor.remove("startRemindDateTime");

        editor.commit();
    }

    private void saveTotalStoppageTime() {
        SharedPreferences.Editor editor = beKindSettings.edit();

        editor.putInt("totalStoppageTimeInSeconds", totalStoppageTimeInSeconds);

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
        if (messageEvent.getPath().equals(REMIND_FINISH)) {
            runnableRemindFinish = new Runnable() {
                public void run() {
                    remindFinishReset();

                    Toast.makeText(getApplicationContext(), R.string.toast_kind_remind,
                            Toast.LENGTH_SHORT).show();
                }
            };

            runOnUiThread(runnableRemindFinish);
        }
    }

    //This message to wear service is still valid
    //Although we may want to send it from our service?
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

    //Deprecated? Or move same notification from wear into BeKindSchedulingService
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
        Context appContext = getApplicationContext();

        Intent service = new Intent(appContext, BeKindSchedulingService.class);

        service.putExtra("_title", getCurrentProgressText());
        service.putExtra("_content", _currentRemindInterval);

        startService(service);
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
        destroyRemindTimer();
        super.onDestroy();
    }
    //end Activity Overrides

    //Timer & Alarm
    private void startAlarm() {
        remindAlarm.setAlarm(this);
        //Bind our service for communicated go notification info
        //Hopefully when Alarm fires it to wake, it will be able to talk to our activity
        if (!BeKindSchedulingService.isRunning()) {
            doBindService();
        }

        remindAlarmRunning = true;
    }

    private void stopAlarm() {
        remindAlarm.cancelAlarm(this);
        doUnbindService();
        remindAlarmRunning = false;
    }

    private void resetCurrentRemindInterval() {
        SharedPreferences.Editor editor = beKindSettings.edit();

        currentRemindInterval = remindInterval;
        totalStoppageTimeInSeconds = 0;

        editor.remove("startRemindDateTime");
        editor.remove("startPauseDateTime");
        editor.remove("stopTimeElapsed");
        editor.remove("totalStoppageTimeInSeconds");

        initTextCountdownValue();

        stopAlarm();
        stopRemindTimerTask();
        btnStart.setText("GO");

        editor.commit();
    }

    private void startRemindTimer() {
        //set a new Timer
        remindTimer = new Timer();

        if (currentRemindInterval == 0 || (currentRemindInterval == 7200 && !remindTimerRunning)) { //initial start
            currentRemindInterval = remindInterval;

            startRemindDateTime = DateTime.now();
            saveStartRemindDateTime();
        }
        else { //resuming
            //append to our total stoppage time, this will be used to add to the startDateTime when app resumes and is running
            totalStoppageTimeInSeconds += Seconds.secondsBetween(startPauseDateTime, DateTime.now()).getSeconds();
            saveTotalStoppageTime();
        }

        //initialize the TimerTask's job
        initializeTimerTask();

        //schedule the timer, after the first 0ms the TimerTask will run every 10000ms
        //Tick every second to update the view, can wrap this with less interruptions later
        //Or... change the update value 10 seconds before their meditation
        remindTimer.schedule(remindTimerTask, 0, 1000);
    }

    private void resumeRemindTimer() {
        remindTimer = new Timer();

        initializeTimerTask();

        remindTimer.schedule(remindTimerTask, 0, 1000);
    }

    private void stopRemindTimerTask() {
        //stop the timer, if it's not already null
        destroyRemindTimer();

        savePauseRemindDateTime();
    }

    private void destroyRemindTimer() {
        if(runnableTimer != null) {
            timerHandler.removeCallbacks(runnableTimer);
            runnableTimer = null;
        }

        if (remindTimer != null) {
            remindTimerRunning = false;
            remindTimer.cancel();
            remindTimer = null;
        }
    }

    private void updateTextCountdownValue(String time) {
        txtCountdown.setText(time);
    }

    private void initTextCountdownValue() {
        updateTextCountdownValue(getFormattedTime(remindInterval));
    }

    private String getFormattedTime(int seconds) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        final SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
        df.setTimeZone(tz);
        return df.format(new Date(seconds * 1000));
    }

    private void initializeTimerTask() {
        runnableTimer = new Runnable() {
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

                //set mobile view values off seconds conversion
                updateTextCountdownValue(currTime);
            }
        };

        remindTimerTask = new TimerTask() {
            public void run() {
                //use a handler to run a toast that shows the current timestamp
                timerHandler.post(runnableTimer);
            }
        };
    }
    //End Timer

    /**
     * Check if the service is running. If the service is running
     * when the activity starts, we want to automatically bind to it.
     */
    private void automaticBind() {
        if (BeKindSchedulingService.isRunning()) {
            doBindService();
        }
    }

    private void sendGoNotificationToService() {
        Hashtable<String, String> messages = new Hashtable<>();
        messages.put("title", getCurrentProgressText());
        messages.put("content", getFormattedTime(currentRemindInterval));

        sendMessageToService(messages);
    }

    /**
     * Send data to the service
     * @param messages The data to send
     */
    private void sendMessageToService(Hashtable<String, String> messages) {
        if (mIsBound) {
            if (mServiceMessenger != null) {
                try {
                    Message msg = Message.obtain(null, BeKindSchedulingService.MSG_ALARM_GET, messages);
                    msg.replyTo = mMessenger;
                    mServiceMessenger.send(msg);
                } catch (RemoteException e) {
                }
            }
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mServiceMessenger = new Messenger(service);
        try {
            Message msg = Message.obtain(null, BeKindSchedulingService.MSG_REGISTER_CLIENT);
            msg.replyTo = mMessenger;
            mServiceMessenger.send(msg);
        }
        catch (RemoteException e) {
            // In this case the service has crashed before we could even do anything with it
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
        mServiceMessenger = null;
    }

    /**
     * Bind this Activity to MyService
     */
    private void doBindService() {
        bindService(new Intent(this, BeKindSchedulingService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    /**
     * Un-bind this Activity to MyService
     */
    private void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            if (mServiceMessenger != null) {
                try {
                    Message msg = Message.obtain(null, BeKindSchedulingService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mServiceMessenger.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    /**
     * Handle incoming messages from MyService
     */
    private class IncomingMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            // Log.d(LOGTAG,"IncomingHandler:handleMessage");
            switch (msg.what) {
                case BeKindSchedulingService.MSG_ALARM_GET:
                    //Alarm requested content when it fired
                    sendGoNotificationToService();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

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
        else if (id == R.id.reset_values) {
            resetAllValues();
        }

        return super.onOptionsItemSelected(item);
    }
    //end Header Menu overrides

}
