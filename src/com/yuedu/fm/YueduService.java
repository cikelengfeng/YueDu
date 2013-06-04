package com.yuedu.fm;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.content.*;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.*;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * Created by xudong on 13-5-19.
 */
public class YueduService extends IntentService {
    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     */

    public static final int TRANSACT_CODE_PLAY = 0;
    public static final int TRANSACT_CODE_PAUSE = 2;
    public static final int TRANSACT_CODE_STOP = 4;

    protected static final String PLAYER_SERVICE_BROADCAST = "player_service_broadcast";
    protected static final String PLAYER_SERVICE_BROADCAST_CATEGORY_CURRENT_POSITION = "player_service_category_current_position";
    protected static final String PLAYER_SERVICE_BROADCAST_EXTRA_CURRENT_POSITION_KEY = "player_service_category_extra_current_position_key";
    protected static final String PLAYER_SERVICE_BROADCAST_EXTRA_DURATION_KEY = "player_service_category_extra_current_duration_key";

    private AudioManager mAudioManager;
    private MediaPlayer mPlayer;
    private AudioManager.OnAudioFocusChangeListener mFocusListener;
    static private final ComponentName REMOTE_CONTROL_RECEIVER_NAME = new ComponentName("com.yuedu.fm", "RemoteControlReceiver");
    private NoisyAudioStreamReceiver mNoisyAudioStreamReceiver;
    private String mDataSource;
    private BroadcastReceiver mActivityBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("yuedu","activity broadcast comed "+intent);
        }
    };

    private CountDownTimer mUIUpdateTimer;

    public CountDownTimer getmUIUpdateTimer() {
        if (mUIUpdateTimer == null) {
            mUIUpdateTimer = new CountDownTimer(mPlayer.getDuration(),500) {
                @Override
                public void onTick(long millisUntilFinished) {
                    long currentPostion = mPlayer.getCurrentPosition();
                    long duration = mPlayer.getDuration();
                    Intent intent = new Intent(PLAYER_SERVICE_BROADCAST);
                    intent.addCategory(PLAYER_SERVICE_BROADCAST_CATEGORY_CURRENT_POSITION);
                    intent.putExtra(PLAYER_SERVICE_BROADCAST_EXTRA_CURRENT_POSITION_KEY,currentPostion);
                    intent.putExtra(PLAYER_SERVICE_BROADCAST_EXTRA_DURATION_KEY,duration);
                    sendLocalBroadcast(intent);
                }

                @Override
                public void onFinish() {

                }
            };
        }
        return mUIUpdateTimer;
    }

    public MediaPlayer getmPlayer() {
        if (mPlayer == null) {
            mPlayer = new MediaPlayer();
            mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    getmUIUpdateTimer().cancel();
                    getmUIUpdateTimer().start();
                }
            });
        }
        return mPlayer;
    }

    private void sendLocalBroadcast(Intent intent) {
        if (intent != null)
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
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
                            play();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS:
                            stop();
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
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mActivityBroadcastReceiver);
        if (mUIUpdateTimer != null) {
            mUIUpdateTimer.cancel();
        }
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mActivityBroadcastReceiver, new IntentFilter(MainPlayer.PLAYER_ACTIVITY_BROADCAST));
        return mBinder;
    }

    private YueduBinder mBinder = new YueduBinder();

    class YueduBinder extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case TRANSACT_CODE_PLAY:
                    String tunePath = data.readString();
                    if (mDataSource == null || !mDataSource.equals(tunePath)) {
                        setTunePath(tunePath);
                        return play();
                    } else {
                        try {
                            getmPlayer().start();
                            return true;
                        }catch (Exception e) {
                            e.printStackTrace();
                        }
                        return false;
                    }
                case TRANSACT_CODE_PAUSE:
                    return pause();
                case TRANSACT_CODE_STOP:
                    return stop();
                default:
                    return false;
            }

        }
    }

    private void setTunePath(String tunePath) {
        try {
            MediaPlayer player = getmPlayer();
            player.reset();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setScreenOnWhilePlaying(true);
            player.setDataSource(tunePath);
            mDataSource = tunePath;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean play() {
        int focus = getmAudioManager().requestAudioFocus(getmFocusListener(), AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (focus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            MediaPlayer player = getmPlayer();
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
                player.prepare();
                prepareToStart();
                player.start();
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
            getmPlayer().stop();
            return true;
        }
        return false;
    }

    private boolean pause() {
        if (getmPlayer().isPlaying()) {
            prepareToPause();
            getmPlayer().pause();
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
    }
}
