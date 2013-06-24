package com.yuedu.fm;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;


/**
 * Created by dong on 13-6-17.
 */
public class StreamingDownloadMediaPlayer {

    final static float MIN_AUDIO_PREPARE_TIME = 10000;//10 seconds
    public enum PlayerState {
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

    private URL mURL;

    private PlayerState mState;
    private boolean mLooping;
    private File mCacheDir;
    private Decoder mDecoder = new Decoder();
    private AudioTrack mAudioTrack;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private PrepareAsyncTask mPrepareTask;
    private PlayAsyncTask mPlayTask;
    private ArrayBlockingQueue<short[]> mBuffer = new ArrayBlockingQueue<short[]>(25);

    private OnPreparedListener mPreparedListener;

    private static abstract class PrepareAsyncTask extends AsyncTask<URL,Void,Void> {
        boolean blocking = false;
    }

    private class PlayAsyncTask extends AsyncTask<Void,Void,Void> {
        private boolean isPaused = false;
        private boolean isStopped = false;
        private ReentrantLock pauseLock = new ReentrantLock();
        private Condition unpaused = pauseLock.newCondition();

        @Override
        protected Void doInBackground(Void... params) {
            while(!isStopped) {
                if (isPaused) {
                    pauseLock.lock();
                    try {
                        unpaused.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        pauseLock.unlock();
                    }
                }
                writeDecodedFrameFromBufferToTrack(mBuffer,mAudioTrack);
            }
            return null;
        }

        public void pause() {
            pauseLock.lock();
            try {
                isPaused = true;
            } finally {
                pauseLock.unlock();
            }
        }

        public void start() {
            if (isCancelled()) {
                throw new IllegalStateException("play task has been stopped and cancelled");
            }
            if (isPaused) {
                pauseLock.lock();
                try {
                    isPaused = false;
                } finally {
                    pauseLock.unlock();
                }
            }else {
                execute(null);
            }
        }

        public void stop() {
            isStopped = true;
            cancel(true);
        }
    }

    static public interface OnPreparedListener {
        void onPrepared(StreamingDownloadMediaPlayer mediaPlayer);
    }

    public PlayerState getState() {
        return mState;
    }

    public boolean isLooping() {
        return mLooping;
    }

    public File getCacheDir() {
        return mCacheDir;
    }

    public void setOnPreparedListener(OnPreparedListener mPreparedListener) {
        this.mPreparedListener = mPreparedListener;
    }

    public void setCacheDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            throw new IllegalArgumentException("input dir is null or not a directory");
        }
        mCacheDir = dir;
    }

    public void setDataSource(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("input stream is null");
        }
        if (mState != PlayerState.IDLE) {
            throw new IllegalStateException("cannot setDataSource in ["+mState +"] state");
        }
        this.mURL = url;
        this.mState = PlayerState.INITIALIZED;
    }

    public void reset() {
        mURL = null;
        mState = PlayerState.IDLE;
        mLooping = false;
        if (mAudioTrack != null && isPlaying()) {
            mAudioTrack.stop();
        }
        int minBufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,44100,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT,minBufferSize,AudioTrack.MODE_STREAM);
    }

    public void prepare() throws IOException, BitstreamException, DecoderException, InterruptedException {
        if (mState == PlayerState.INITIALIZED || mState == PlayerState.STOPPED || mState == PlayerState.PREPARING) {
            handleInput(mURL,mDecoder,false);
        }else {
            throw new IllegalStateException("cannot setDataSource in ["+mState +"] state");
        }
    }

    protected void handleInput(final URL url,final Decoder decoder, final boolean isAsynchronous) throws IOException, BitstreamException, DecoderException, InterruptedException {
        boolean shouldCache = (getCacheDir() != null && getCacheDir().isDirectory());
        FileOutputStream fileOutputStream;
        final BufferedOutputStream bufferedOutputStream;
        if (shouldCache) {
            fileOutputStream = new FileOutputStream(new File(getCacheDir(), SystemClock.elapsedRealtime()+".mp3"));
            bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        }else {
            bufferedOutputStream = null;
        }
        mPrepareTask = new PrepareAsyncTask() {
            @Override
            protected Void doInBackground(URL... params) {
                URL url1 = params[0];
                HttpURLConnection connection = null;
                InputStream inputStream = null;
                Bitstream bitstream = null;
                try {
                    connection = (HttpURLConnection) url1.openConnection();
                    connection.connect();
                    inputStream = connection.getInputStream();
                    bitstream = new Bitstream(inputStream);
                    Header header;
                    float decodedAudioTime = 0;
                    float oneShotAudioTime = (float) (1000.0/44.1);
                    boolean prepared = false;
                    while ((header = bitstream.readFrame()) != null) {
                        SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                        writeDecodedFrameToBuffer(mBuffer, buffer.getBuffer());
                        if (bufferedOutputStream != null) {
                            writeDecodedFrameToFile(bufferedOutputStream, buffer.getBuffer());
                        }
                        decodedAudioTime += oneShotAudioTime;
                        if (decodedAudioTime >= MIN_AUDIO_PREPARE_TIME && !prepared) {
                            Log.d("yuedu","buffer is prepared!!!!!!!!!!!!!");
                            prepared = true;
                            if (blocking) {
                                notifyAll();
                                blocking = false;
                            }
                            notifyPrepared();
                        }
                        bitstream.closeFrame();
                    }
                    if (blocking) {
                        notifyAll();
                        blocking = false;
                    }
                    if (bufferedOutputStream != null) {
                        bufferedOutputStream.flush();
                        bufferedOutputStream.close();
                    }
                    if (bitstream != null) {
                        bitstream.close();
                    }
                    if (inputStream != null) {
                        inputStream.close();
                    }
                }catch (Exception e) {
                    e.printStackTrace();
                }finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
                return null;
            }
        };
        synchronized (mPrepareTask) {
            mPrepareTask.execute(url);
            if (isAsynchronous) {
                mState = PlayerState.PREPARING;
            }else {
                mPrepareTask.wait();
                mPrepareTask.blocking = true;
            }
        }
    }

    private void notifyPrepared() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mState = PlayerState.PREPARED;
                if (mPreparedListener != null) {
                    mPreparedListener.onPrepared(StreamingDownloadMediaPlayer.this);
                }
            }
        });
    }

    private void writeDecodedFrameFromBufferToTrack(final ArrayBlockingQueue<short[]> buffer,final AudioTrack track) {
        if (buffer.remainingCapacity() == 0) {
            short[] frame = readDecodedFrameFromBuffer(buffer);
            track.write(frame,0,frame.length);
        }
    }


    private void writeDecodedFrameToBuffer(final ArrayBlockingQueue<short[]> buffer,short[] frameData) throws InterruptedException {
        buffer.offer(frameData);
    }

    private short[] readDecodedFrameFromBuffer(final ArrayBlockingQueue<short[]> buffer) {
        return buffer.poll();
    }

    protected void writeDecodedFrameToFile(final OutputStream outputStream,short[] frameData) throws IOException {
        for (short s : frameData) {
            outputStream.write((byte)(s & 0xff));
            outputStream.write((byte)((s >> 8) & 0xff));
        }
    }


    public void prepareAsync() throws DecoderException, InterruptedException, BitstreamException, IOException {
        if (mState == PlayerState.INITIALIZED || mState == PlayerState.STOPPED) {
            handleInput(mURL,mDecoder,true);
        }else {
            throw new IllegalStateException("cannot setDataSource in ["+mState +"] state");
        }
    }

    public void start() {
        if (mState == PlayerState.PREPARED || mState == PlayerState.COMPLETED || mState == PlayerState.PAUSED) {
            mState = PlayerState.STARTED;
            mAudioTrack.play();
            if (mPlayTask == null) {
                mPlayTask = new PlayAsyncTask();
                mPlayTask.start();
            }
        }else {
            throw new IllegalStateException("cannot setDataSource in ["+mState +"] state");
        }
    }

    public void pause() {
        if (mState == PlayerState.STARTED) {
            mState = PlayerState.PAUSED;
            mAudioTrack.pause();
            mPlayTask.pause();
        }else {
            throw new IllegalStateException("cannot setDataSource in ["+mState +"] state");
        }
    }

    public void stop() {
        if (mState == PlayerState.PREPARED || mState == PlayerState.COMPLETED || mState == PlayerState.PAUSED || mState == PlayerState.STARTED) {
            mState = PlayerState.STOPPED;
            mAudioTrack.stop();
            mPlayTask.stop();
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
        if (isPlaying()) {
            mAudioTrack.stop();
        }
        mAudioTrack = null;
        mCacheDir = null;
        mDecoder = null;
        mHandler = null;
        mURL = null;
        mPreparedListener = null;
        if (mPrepareTask != null) {
            if (!mPrepareTask.isCancelled()) {
                mPrepareTask.cancel(true);
            }
            mPrepareTask.notifyAll();
            mPrepareTask = null;
        }
    }

}
