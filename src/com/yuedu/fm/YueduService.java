package com.yuedu.fm;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.yuedu.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javazoom.jl.decoder.JavaLayerException;

/**
 *
 * Created by xudong on 13-5-19.
 */
public class YueduService extends IntentService {

    /**
     * intent action
     */
    protected static final String PLAYER_SERVICE_BROADCAST = "player_service_broadcast";
    /**
     * intent category
     */
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_CURRENT_POSITION = "player_service_category_current_position";
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PREPARE = "player_service_category_player_will_prepare";
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PREPARED = "player_service_category_player_prepared";
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PLAY = "player_service_category_player_will_play";
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PLAYING = "player_service_category_player_playing";
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PAUSE = "player_service_category_player_will_pause";
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PAUSED = "player_service_category_player_paused";
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_STOP = "player_service_category_player_will_stop";
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_STOPPED = "player_service_category_player_stopped";
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_ERROR_OCCURRED = "player_service_category_player_error_occurred";
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_COMPLETE = "player_service_category_player_complete";
    private static final int ONGOING_NOTIFICATION_ID = 0x77<<7;


    private void sendPreparedBroadcast() {
        Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
        intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PREPARED);
        sendLocalBroadcast(intent);
    }

    private void sendWillPrepareBroadcast() {
        Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
        intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PREPARE);
        sendLocalBroadcast(intent);
    }

    private void sendWillPlayBroadcast() {
        Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
        intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PLAY);
        sendLocalBroadcast(intent);
    }

    private void sendPlayingBroadcast() {
        Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
        intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PLAYING);
        sendLocalBroadcast(intent);
    }

    private void sendWillPauseBroadcast() {
        Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
        intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PAUSE);
        sendLocalBroadcast(intent);
    }

    private void sendPausedBroadcast() {
        Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
        intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PAUSED);
        sendLocalBroadcast(intent);
    }

    private void sendWillStopBroadcast() {
        Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
        intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_STOP);
        sendLocalBroadcast(intent);
    }

    private void sendStoppedBroadcast() {
        Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
        intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_STOPPED);
        sendLocalBroadcast(intent);
    }

    private void sendErrorOccurredBroadcast(String error) {
        Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
        intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_ERROR_OCCURRED);
        intent.putExtra(PLAYER_SERVICE_BROADCAST_EXTRA_ERROR_KEY, error);
        sendLocalBroadcast(intent);
    }

    private void sendCompletionBroadcast() {
        Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
        intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_COMPLETE);
        sendLocalBroadcast(intent);
    }

    /**
     * intent extra key
     */
    protected static final String PLAYER_SERVICE_BROADCAST_EXTRA_CURRENT_POSITION_KEY = "player_service_category_extra_current_position_key";
    protected static final String PLAYER_SERVICE_BROADCAST_EXTRA_DURATION_KEY = "player_service_category_extra_current_duration_key";
    protected static final String PLAYER_SERVICE_BROADCAST_EXTRA_ERROR_KEY = "player_service_category_extra_tune_path_key";

    private AudioManager mAudioManager;
    private TelephonyManager mTelephonyManager;
    private StreamingDownloadMediaPlayer mPlayer;
    private AudioManager.OnAudioFocusChangeListener mFocusListener;
    private PhoneStateListener mPhoneStateListener;
    static private final ComponentName REMOTE_CONTROL_RECEIVER_NAME = new ComponentName("com.yuedu.fm", "RemoteControlReceiver");
    private NoisyAudioStreamReceiver mNoisyAudioStreamReceiver;

    private BroadcastReceiver mActivityBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Set<String> categories = intent.getCategories();
            if (categories.contains(MainPlayer.PLAYER_ACTIVITY_BROADCAST_CATEGORY_PLAY)) {
                String path = DataAccessor.SINGLE_INSTANCE.getPlayingTune().mp3URL;
                if (!TextUtils.isEmpty(path) && (getCurrentDataSource() == null || !path.equals(getCurrentDataSource()))) {
                    prepareForPath(path);
                } else {
                    try {
                        play();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (categories.contains(MainPlayer.PLAYER_ACTIVITY_BROADCAST_CATEGORY_PAUSE)) {
                pause();
            }
        }
    };

    private void prepareForPath(String path) {
        if (mScheduler != null) {
            getmScheduler().purge();
            getmScheduler().pause();
        }
        try {
            if (getmPlayer().isPlaying() || getmPlayer().isPaused() || getmPlayer().isCompleted() || getmPlayer().isPreparing()) {
                sendWillStopBroadcast();
                getmPlayer().stop();
                sendStoppedBroadcast();
            }
            setTunePath(path);
            prepareToPlay();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getCurrentDataSource() {
        return getmPlayer().getDataSource()==null?null:getmPlayer().getDataSource().toString();
    }

    private PausableThreadPoolExecutor mScheduler;

    public PausableThreadPoolExecutor getmScheduler() {
        if (mScheduler == null) {
            mScheduler = new PausableThreadPoolExecutor(1);
            mScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
            mScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);
            mScheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    long currentPosition = getmPlayer().getCurrentPosition();
                    long duration = getmPlayer().getDuration();
                    Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
                    intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_CURRENT_POSITION);
                    intent.putExtra(PLAYER_SERVICE_BROADCAST_EXTRA_CURRENT_POSITION_KEY, currentPosition);
                    intent.putExtra(PLAYER_SERVICE_BROADCAST_EXTRA_DURATION_KEY, duration);
                    sendLocalBroadcast(intent);
                }
            }, 0, 500, TimeUnit.MILLISECONDS);
        }
        return mScheduler;
    }

    public StreamingDownloadMediaPlayer getmPlayer() {
        if (mPlayer == null) {
            mPlayer = new StreamingDownloadMediaPlayer();
            File diskFileCacheDir = new File(getExternalCacheDir(),"audio");
            if (diskFileCacheDir.exists() || diskFileCacheDir.mkdirs()) {
                mPlayer.setCacheDir(diskFileCacheDir);
            }
            //TODO listener API
            mPlayer.setOnPreparedListener(new StreamingDownloadMediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(StreamingDownloadMediaPlayer mp) {
                    sendPreparedBroadcast();
                    prepareToStart();
                    play();
                    getmScheduler().purge();
                    getmScheduler().resume();
                }
            });
            mPlayer.setOnCompletionListener(new StreamingDownloadMediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(StreamingDownloadMediaPlayer mediaPlayer) {
                    if (mScheduler != null) {
                        getmScheduler().purge();
                        getmScheduler().pause();
                    }
                    sendCompletionBroadcast();
                    DataAccessor.SINGLE_INSTANCE.playNextTune();
                    prepareForPath(DataAccessor.SINGLE_INSTANCE.getPlayingTune().mp3URL);
                }
            });
            mPlayer.setOnErrorListener(new StreamingDownloadMediaPlayer.OnErrorListener() {
                @Override
                public void onError(StreamingDownloadMediaPlayer mediaPlayer, Throwable e) {
                    if (mScheduler != null) {
                        getmScheduler().purge();
                        getmScheduler().pause();
                    }
                    String error;
                    if (e instanceof FileNotFoundException) {
                        error = "未发现网络音频文件";
                    }else {
                        error = e.getLocalizedMessage();
                    }
                    sendErrorOccurredBroadcast(error);
                }
            });
        }
        return mPlayer;
    }

    private void sendLocalBroadcast(Intent intent) {
        if (intent != null) {
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }
    }

    private void registerLocalBroadcastReceiver() {
        assert mActivityBroadcastReceiver != null;
        IntentFilter filter = new IntentFilter(MainPlayer.PLAYER_ACTIVITY_BROADCAST);
        filter.addCategory(MainPlayer.PLAYER_ACTIVITY_BROADCAST_CATEGORY_PAUSE);
        filter.addCategory(MainPlayer.PLAYER_ACTIVITY_BROADCAST_CATEGORY_PLAY);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mActivityBroadcastReceiver, filter);
    }

    private void unregisterLocalBroadcastReceiver() {
        assert mActivityBroadcastReceiver != null;
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mActivityBroadcastReceiver);
    }

    public AudioManager getmAudioManager() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        }
        return mAudioManager;
    }

    public TelephonyManager getmTelephonyManager() {
        if (mTelephonyManager == null) {
            mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        }
        return mTelephonyManager;
    }

    public AudioManager.OnAudioFocusChangeListener getmFocusListener() {
        if (mFocusListener == null) {
            mFocusListener = new AudioManager.OnAudioFocusChangeListener() {
                @TargetApi(Build.VERSION_CODES.FROYO)
                public void onAudioFocusChange(int focusChange) {
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            pause();
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            prepareToPlay();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS:
                            pause();
                        default:
                            break;
                    }
                }
            };
        }
        return mFocusListener;
    }

    public PhoneStateListener getmPhoneStateListener() {
        if (mPhoneStateListener == null) {
            mPhoneStateListener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String incomingNumber) {
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        //Incoming call: Pause music
                        if (getmPlayer().isPlaying()) {
                            pause();
                        }
                        Log.d("yuedu", "incoming call!!!! number is "+incomingNumber);
                    } else if(state == TelephonyManager.CALL_STATE_IDLE) {
                        //Not in call: Play music
                        if (getmPlayer().isPaused()) {
                            play();
                        }
                        Log.d("yuedu", "not in call!!!!");
                    } else if(state == TelephonyManager.CALL_STATE_OFFHOOK) {
                        //A call is dialing, active or on hold
                        if (getmPlayer().isPlaying()) {
                            pause();
                        }
                        Log.d("yuedu", "A call is dialing, active or on hold!!!!" +incomingNumber);
                    }
                    super.onCallStateChanged(state, incomingNumber);
                }
            };
        }
        return mPhoneStateListener;
    }

    public NoisyAudioStreamReceiver getmNoisyAudioStreamReceiver() {
        if (mNoisyAudioStreamReceiver == null) {
            mNoisyAudioStreamReceiver = new NoisyAudioStreamReceiver();
        }
        return mNoisyAudioStreamReceiver;
    }

    public YueduService() {
        super("YueduService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }


    @Override
    public boolean onUnbind(Intent intent) {
        unregisterLocalBroadcastReceiver();
        TelephonyManager telMgr = getmTelephonyManager();
        if (telMgr != null) {
            telMgr.listen(getmPhoneStateListener(),PhoneStateListener.LISTEN_NONE);
        }
        if (mScheduler != null) {
            mScheduler.purge();
            mScheduler.shutdownNow();
        }
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("yuedu","on start command ");
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DataAccessor.SINGLE_INSTANCE.downloadData(this);
        registerLocalBroadcastReceiver();
        TelephonyManager telMgr = getmTelephonyManager();
        if (telMgr != null) {
            Log.d("yuedu","start listen phone state");
            telMgr.listen(getmPhoneStateListener(),PhoneStateListener.LISTEN_CALL_STATE);
        }
        Intent notificationIntent = new Intent(this, MainPlayer.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
//        Notification notification = new Notification();
//        notification.contentIntent = pendingIntent;
//        notification.icon = R.drawable.ic_launcher;
        Notification notification = new Notification.Builder(this).setSmallIcon(R.drawable.ic_launcher).setContentIntent(pendingIntent).build();
        startForeground(ONGOING_NOTIFICATION_ID, notification);
    }

    private void setTunePath(final String tunePath) throws IOException, JavaLayerException {
        StreamingDownloadMediaPlayer player = getmPlayer();
        player.reset();
        player.setDataSource(new URL(tunePath));
    }

    private void play() {
        sendWillPlayBroadcast();
        getmPlayer().start();
        sendPlayingBroadcast();
    }

    private boolean prepareToPlay() {
        AudioManager audioMgr = getmAudioManager();
        if (audioMgr != null) {
            int focus = audioMgr.requestAudioFocus(getmFocusListener(), AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            if (focus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                StreamingDownloadMediaPlayer player = getmPlayer();
                try {
                    sendWillPrepareBroadcast();
                    player.prepareAsync();
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                //request audio focus failed
            }
        }
        return false;
    }

    private boolean stop() {
        if (getmPlayer().isPlaying()) {
            prepareToStop();
            sendWillStopBroadcast();
            getmPlayer().stop();
            sendStoppedBroadcast();
            return true;
        }
        return false;
    }

    private boolean pause() {
        if (getmPlayer().isPlaying()) {
            prepareToPause();
            sendWillPauseBroadcast();
            getmPlayer().pause();
            sendPausedBroadcast();
            return true;
        }
        return false;
    }

    private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private void prepareToStart() {
        registerReceiver(getmNoisyAudioStreamReceiver(), intentFilter);
    }

    private void prepareToStop() {
        unregisterReceiver(getmNoisyAudioStreamReceiver());
        AudioManager audioMgr = getmAudioManager();
        if (audioMgr != null) {
            audioMgr.unregisterMediaButtonEventReceiver(REMOTE_CONTROL_RECEIVER_NAME);
            audioMgr.abandonAudioFocus(getmFocusListener());
        }
    }

    private void prepareToPause() {
        AudioManager audioMgr = getmAudioManager();
        if (audioMgr != null) {
            audioMgr.abandonAudioFocus(getmFocusListener());
        }
    }

    private class NoisyAudioStreamReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                pause();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
        if (mPlayer != null) {
            mPlayer.release();
        }
        stopForeground(true);
    }

    static class PausableThreadPoolExecutor extends ScheduledThreadPoolExecutor {
        private boolean isPaused;
        private ReentrantLock pauseLock = new ReentrantLock();
        private Condition unpaused = pauseLock.newCondition();

        public PausableThreadPoolExecutor(int corePoolSize) {
            super(corePoolSize);
        }

        protected void beforeExecute(Thread t, Runnable r) {
            super.beforeExecute(t, r);
            pauseLock.lock();
            try {
                while (isPaused) unpaused.await();
            } catch (InterruptedException ie) {
                t.interrupt();
            } finally {
                pauseLock.unlock();
            }
        }

        public void pause() {
            pauseLock.lock();
            try {
                isPaused = true;
            } finally {
                pauseLock.unlock();
            }
        }

        public void resume() {
            pauseLock.lock();
            try {
                isPaused = false;
                unpaused.signalAll();
            } finally {
                pauseLock.unlock();
            }
        }
    }
}
