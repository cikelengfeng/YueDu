package com.yuedu.fm;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

enum PlayerState {
    IDLE,
    INITIALIZED,
    PREPARING,
    PREPARED,
    STARTED,
    STOPPED,
    PAUSED,
    COMPLETED,
    END;

    @Override
    public String toString() {
        return readableMap.get(this);
    }

    private static Map<PlayerState,String> readableMap;
    static {
        readableMap = new HashMap<PlayerState, String>(9);
        readableMap.put(IDLE,"IDLE");
        readableMap.put(INITIALIZED,"INITIALIZED");
        readableMap.put(PREPARING,"PREPARING");
        readableMap.put(PREPARED,"PREPARED");
        readableMap.put(STARTED,"STARTED");
        readableMap.put(STOPPED,"STOPPED");
        readableMap.put(PAUSED,"PAUSED");
        readableMap.put(COMPLETED,"COMPLETED");
        readableMap.put(END,"END");
    }
}

/**
 * Created by dong on 13-6-17.
 */
public class StreamingDownloadMediaPlayer {

    private InputStream mInputStream;
    private PlayerState mState;
    private boolean mLooping;
    private File mCacheDir;

    public InputStream getInputStream() {
        return mInputStream;
    }

    public PlayerState getState() {
        return mState;
    }

    public boolean isLooping() {
        return mLooping;
    }

    public File getCacaheDir() {
        return mCacheDir;
    }

    public void setCacheDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            throw new IllegalArgumentException("input dir is null or not a directory");
        }
        mCacheDir = dir;
    }

    public void setDataSource(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("input stream is null");
        }
        if (mState != PlayerState.IDLE) {
            throw new IllegalStateException("cannot setDataSource in ["+mState +"] state");
        }
        this.mInputStream = inputStream;
        this.mState = PlayerState.INITIALIZED;
    }

    public void reset() {
        mInputStream = null;
        mState = PlayerState.IDLE;
        mLooping = false;
    }

    public void prepare() {
        if (mState == PlayerState.INITIALIZED || mState == PlayerState.STOPPED || mState == PlayerState.PREPARING) {
            //TODO
        }else {
            throw new IllegalStateException("cannot setDataSource in ["+mState +"] state");
        }
    }

    public void prepareAsync() {
        if (mState == PlayerState.INITIALIZED || mState == PlayerState.STOPPED) {
            //TODO
        }else {
            throw new IllegalStateException("cannot setDataSource in ["+mState +"] state");
        }
    }

    public void start() {
        if (mState == PlayerState.PREPARED || mState == PlayerState.COMPLETED || mState == PlayerState.PAUSED) {
            //TODO
        }else {
            throw new IllegalStateException("cannot setDataSource in ["+mState +"] state");
        }
    }

    public void pause() {
        if (mState == PlayerState.STARTED) {
            //TODO
        }else {
            throw new IllegalStateException("cannot setDataSource in ["+mState +"] state");
        }
    }

    public void stop() {
        if (mState == PlayerState.PREPARED || mState == PlayerState.COMPLETED || mState == PlayerState.PAUSED || mState == PlayerState.STARTED) {
            //TODO
        }else {
            throw new IllegalStateException("cannot setDataSource in ["+mState +"] state");
        }
    }

    public void seekTo(long millisecond) {
        //TODO
    }

    public long getCurrentPosition() {
        //TODO
        return 0;
    }

    public long getDuration() {
        //TODO
        return 0;
    }

    public boolean isPlaying() {
        return mState == PlayerState.STARTED;
    }

    public void release() {
        //TODO release buffer
    }
}
