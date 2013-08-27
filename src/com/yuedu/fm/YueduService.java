package com.yuedu.fm;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;

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
    private StreamingDownloadMediaPlayer mPlayer;
    private AudioManager.OnAudioFocusChangeListener mFocusListener;
    static private final ComponentName REMOTE_CONTROL_RECEIVER_NAME = new ComponentName("com.yuedu.fm", "RemoteControlReceiver");
    private NoisyAudioStreamReceiver mNoisyAudioStreamReceiver;
    private String mDataSource;
    private BroadcastReceiver mActivityBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Set<String> categories = intent.getCategories();
            if (categories.contains(MainPlayer.PLAYER_ACTIVITY_BROADCAST_CATEGORY_PLAY)) {
                String path = intent.getStringExtra(MainPlayer.PLAY_TUNE_INTENT_EXTRA_PATH_KEY);
                if (mDataSource == null || !mDataSource.equals(path)) {
                    if (mScheduler != null) {
                        getmScheduler().purge();
                        getmScheduler().pause();
                    }
                    try {
                        if (getmPlayer().isPlaying() || getmPlayer().isPaused() || getmPlayer().isCompleted()) {
                            sendWillStopBroadcast();
                            getmPlayer().stop();
                            sendStoppedBroadcast();
                        }
                        setTunePath(path);
                        prepareToPlay();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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
        if (mScheduler != null) {
            mScheduler.purge();
            mScheduler.shutdownNow();
        }
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        registerLocalBroadcastReceiver();
        return mBinder;
    }

    private YueduBinder mBinder = new YueduBinder();

    class YueduBinder extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            return false;
        }
    }

    private void setTunePath(final String tunePath) throws IOException, JavaLayerException {
        mDataSource = tunePath;
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
        int focus = getmAudioManager().requestAudioFocus(getmFocusListener(), AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
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
        getmAudioManager().unregisterMediaButtonEventReceiver(REMOTE_CONTROL_RECEIVER_NAME);
        getmAudioManager().abandonAudioFocus(getmFocusListener());
    }

    private void prepareToPause() {
        getmAudioManager().abandonAudioFocus(getmFocusListener());
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
