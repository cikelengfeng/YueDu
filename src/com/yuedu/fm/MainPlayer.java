package com.yuedu.fm;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.yuedu.R;
import com.yuedu.image.ImageCache.ImageCacheParams;
import com.yuedu.image.ImageFetcher;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import android.os.Bundle;

public class MainPlayer extends FragmentActivity {

    private static final int NEVER_PLAYED = -1;
    private static final int PLAYED = 0;
    protected static final String PLAYER_ACTIVITY_BROADCAST = "player_activity_broadcast";
    protected static final String PLAYER_ACTIVITY_BROADCAST_CATEGORY_PLAY = "player_activity_broadcast_category_play";
    protected static final String PLAYER_ACTIVITY_BROADCAST_CATEGORY_PAUSE = "player_activity_broadcast_category_pause";

    protected static final String PLAY_TUNE_INTENT_EXTRA_PATH_KEY = "player_activity_play_tune_path_key";

    private BroadcastReceiver mServiceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long currentPosition = intent.getLongExtra(YueduService.PLAYER_SERVICE_BROADCAST_EXTRA_CURRENT_POSITION_KEY,0);
            setCurrentPosition((int) currentPosition);
        }
    };

    private void setCurrentPosition(int currentPosition) {
        assert currentPosition >= 0;
        Log.d("yuedu","set progress bar current postion "+currentPosition + " progress max : "+getmProgressBar().getMax());
        getmProgressBar().incrementProgressBy(currentPosition - getmProgressBar().getProgress());
        int min = currentPosition/60000;
        int sec = currentPosition%60000/1000;
        getmPlayedTimeTextView().setText(min + ":" + sec);
    }

    private AsyncHttpClient mClient;
    private ArrayList<JSONObject> mPlaylist;
    private int mState = NEVER_PLAYED;
    private int mPlayingTuneIndex = 0;
    private PlaylistAdapter mAdapter;

    private ListView mListView;
    private RelativeLayout mListViewContainer;
    private ImageView mImageView;
    private ImageFetcher mImageFetcher;
    private TextView mTitleView;
    private TextView mInfoView;
    private ImageButton mListButton;
    private ImageButton mPlayButton;
    private ImageButton mNextButton;
    private ProgressBar mProgressBar;
    private TextView mPlayedTimeTextView;

    public TextView getmPlayedTimeTextView() {
        if (mPlayedTimeTextView == null) {
            mPlayedTimeTextView = (TextView) findViewById(R.id.tune_played_time_tv);
        }
        return mPlayedTimeTextView;
    }

    public ProgressBar getmProgressBar() {
        if (mProgressBar == null) {
            mProgressBar = (ProgressBar) findViewById(R.id.tune_progress_pb);
        }
        return mProgressBar;
    }

    private int getCurrentPlayingTuneDuration() {
        JSONObject tune = getmPlaylist().get(mPlayingTuneIndex);
        int min = tune.optInt("min",0);
        int sec = tune.optInt("sec",0);
        return (min * 60 + sec)*1000;
    }

    public AsyncHttpClient getClient() {
        if (mClient == null) {
            mClient = new AsyncHttpClient();
        }
        return mClient;
    }

    public ListView getListView() {
        if (mListView == null) {
            mListView = (ListView) findViewById(R.id.playlist_lv);
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    changeTuneAtIndex(position);
                }
            });
        }
        return mListView;
    }

    public ArrayList<JSONObject> getmPlaylist() {
        if (mPlaylist == null) {
            mPlaylist = new ArrayList<JSONObject>();
        }
        return mPlaylist;
    }

    public RelativeLayout getmListViewContainer() {
        if (mListViewContainer == null) {
            mListViewContainer = (RelativeLayout) findViewById(R.id.playlist_ll);
        }
        return mListViewContainer;
    }

    public PlaylistAdapter getAdapter() {
        if (mAdapter == null) {
            mAdapter = new PlaylistAdapter(getApplicationContext(), getmPlaylist(), getmImageFetcher());
        }
        return mAdapter;
    }

    public ImageView getmImageView() {
        if (mImageView == null) {
            mImageView = (ImageView) findViewById(R.id.tune_cover_iv);
        }
        return mImageView;
    }

    public TextView getmInfoView() {
        if (mInfoView == null) {
            mInfoView = (TextView) findViewById(R.id.tune_info_tv);
        }
        return mInfoView;
    }

    public TextView getmTitleView() {
        if (mTitleView == null) {
            mTitleView = (TextView) findViewById(R.id.tune_name_tv);
        }
        return mTitleView;
    }

    public ImageFetcher getmImageFetcher() {
        if (mImageFetcher == null) {
            ImageCacheParams cacheParams = new ImageCacheParams(getApplicationContext(),
                    "yueducache");

            cacheParams.setMemCacheSizePercent(0.6f); // Set memory cache to 60% of app memory
            mImageFetcher = new ImageFetcher(getApplicationContext(), -1);
            mImageFetcher.addImageCache(getSupportFragmentManager(), cacheParams);
        }
        return mImageFetcher;
    }

    public ImageButton getmListButton() {
        if (mListButton == null) {
            mListButton = (ImageButton) findViewById(R.id.playlist_ib);
        }
        return mListButton;
    }

    public ImageButton getmPlayButton() {
        if (mPlayButton == null) {
            mPlayButton = (ImageButton) findViewById(R.id.play_ib);
        }
        return mPlayButton;
    }

    public ImageButton getmNextButton() {
        if (mNextButton == null) {
            mNextButton = (ImageButton) findViewById(R.id.nexttune_ib);
        }
        return mNextButton;
    }

    public void setPlaylist(JSONArray list) {
        getAdapter().clear();
        try {
            for (int i = 0; i < list.length(); i++) {
                JSONObject tune;
                tune = list.getJSONObject(i);
                getmPlaylist().add(tune);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        getListView().setAdapter(mAdapter);
    }


    public void setCover(JSONObject tune) {
        String url = tune.optString("bg", "");
        String title = tune.optString("title", "");
        String author = tune.optString("author", "");
        String player = tune.optString("player", "");
        String info = "author:" + author + " player:" + player;
        getmImageFetcher().loadImage(url, getmImageView());
        getmTitleView().setText(title);
        getmInfoView().setText(info);
        getmProgressBar().setMax(getCurrentPlayingTuneDuration());
        getmPlayedTimeTextView().setText("00:00");
    }

    private YueduServiceConnection mServiceConnection = new YueduServiceConnection();

    class YueduServiceConnection implements ServiceConnection {

        private IBinder mServiceBinder;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mServiceBinder = service;
            Log.d("yuedu","service connected "+mServiceBinder);
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(mNetworkStateReceiver, filter);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }

    private BroadcastReceiver mNetworkStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo info = cm.getActiveNetworkInfo();
            assert info != null;
            Log.w("yuedu", "Network Type Changed "+info);
            if (info.getType() == ConnectivityManager.TYPE_WIFI && info.getState() == NetworkInfo.State.CONNECTED) {
                Log.d("yuedu","wifi is connected");
                if (mState != NEVER_PLAYED) {
                    Log.d("yuedu","resume playing");
                    play();
                }
            }else {
                Log.w("yuedu","wifi is disconnected");
                if (mState != NEVER_PLAYED) {
                    Log.d("yuedu","pause playing");
                    pausePlay();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_player);
        Intent intent = new Intent(getApplicationContext(), YueduService.class);
        bindService(intent, mServiceConnection, BIND_AUTO_CREATE | BIND_IMPORTANT);
        registerLocalBroadcastReceiver();
        RequestParams param = new RequestParams("data", "playlist");
        getClient().get("http://yuedu.fm/", param, new JsonHttpResponseHandler() {

            @Override
            public void onSuccess(JSONObject jsonObject) {
                super.onSuccess(jsonObject);
                JSONArray al = jsonObject.optJSONArray("list");
                Log.d("yuedu","play list received "+al);
                setPlaylist(al);
                JSONObject tune = getmPlaylist().get(0);
                setCover(tune);
            }

            @Override
            public void onFailure(Throwable throwable, JSONObject jsonObject) {
                super.onFailure(throwable, jsonObject);
            }
        });


        getmListButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getmListViewContainer().setVisibility(getmListViewContainer().getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        getmPlayButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setSelected(!v.isSelected());
                if (v.isSelected()) {
                    play();
                }else {
                    pausePlay();
                }
            }
        });
        getmNextButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNextTune();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterLocalBroadcastReceiver();
    }

    private void play() {
        if (mPlayingTuneIndex < getmPlaylist().size()) {
            JSONObject tune = getmPlaylist().get(mPlayingTuneIndex);
            startPlay(tune);
        }
    }

    private void changeTuneAtIndex(int index) {
        mPlayingTuneIndex = index;
        if (index < getmPlaylist().size()) {
            JSONObject tune = getmPlaylist().get(index);
            setCover(tune);
            startPlay(tune);
        }
    }

    private void startPlay(JSONObject tune) {
        String path = tune.optString("mp3");
        if (path != null && path.length() > 0) {
            Intent intent = new Intent(PLAYER_ACTIVITY_BROADCAST);
            intent.addCategory(PLAYER_ACTIVITY_BROADCAST_CATEGORY_PLAY);
            intent.putExtra(PLAY_TUNE_INTENT_EXTRA_PATH_KEY,path);
            sendLocalBroadcast(intent);
            //TODO remove to receiver
            getmPlayButton().setSelected(true);
            mState = PLAYED;
        }else {
            //TODO show warning
        }
    }

    private void pausePlay() {
        Intent intent = new Intent(PLAYER_ACTIVITY_BROADCAST);
        intent.addCategory(PLAYER_ACTIVITY_BROADCAST_CATEGORY_PAUSE);
        sendLocalBroadcast(intent);
    }

    private void playNextTune() {
        if (mPlayingTuneIndex >= getmPlaylist().size() - 1) {
            return;
        }
        changeTuneAtIndex(mPlayingTuneIndex + 1);
    }

    private void sendLocalBroadcast(Intent intent) {
        assert intent != null;
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private void registerLocalBroadcastReceiver() {
        assert mServiceBroadcastReceiver != null;
        IntentFilter filter = new IntentFilter(YueduService.PLAYER_SERVICE_BROADCAST);
        filter.addCategory(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_CURRENT_POSITION);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mServiceBroadcastReceiver,filter);
    }

    private void unregisterLocalBroadcastReceiver() {
        assert mServiceBroadcastReceiver != null;
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mServiceBroadcastReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_player, menu);
        return true;
    }

    static private class PlaylistAdapter extends ArrayAdapter<JSONObject> {

        private LayoutInflater inflater;
        private ImageFetcher imageFetcher;

        public PlaylistAdapter(Context context, ArrayList<JSONObject> list, ImageFetcher fetcher) {
            super(context, 0, list);
            inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            imageFetcher = fetcher;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            JSONObject tune = getItem(position);
            String url = tune.optString("img", "");
            String titleStr = tune.optString("title", "");
            String author = tune.optString("author", "");
            String player = tune.optString("player", "");
            String infoStr = "author:" + author + " player:" + player;
            String timeStr = tune.optString("min", "00") + ":" + tune.optString("sec", "00");
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.playlist_item, null);
            }
            assert convertView != null;
            ImageView thumb = (ImageView) convertView.findViewById(R.id.tune_item_thumb_iv);
            TextView title = (TextView) convertView.findViewById(R.id.tune_item_title_tv);
            TextView info = (TextView) convertView.findViewById(R.id.tune_item_info_tv);
            TextView time = (TextView) convertView.findViewById(R.id.tune_item_time_tv);
            imageFetcher.loadImage(url, thumb);
            title.setText(titleStr);
            info.setText(infoStr);
            time.setText(timeStr);
            return convertView;
        }
    }

    public class RemoteControlReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        play();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        pausePlay();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        playNextTune();
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Signature getApkSignature(String apkPath) throws Exception {
        Class clazz = Class.forName("android.content.pm.PackageParser");
        Method method = clazz.getMethod("parsePackage", File.class,
                String.class, DisplayMetrics.class, int.class);

        Object packageParser = clazz.getConstructor(String.class).newInstance(
                "");
        Object packag = method.invoke(packageParser, new File(apkPath), null,
                this.getResources().getDisplayMetrics(), 0x0004);

        method = clazz.getMethod("collectCertificates",
                Class.forName("android.content.pm.PackageParser$Package"),
                int.class);
        method.invoke(packageParser, packag, PackageManager.GET_SIGNATURES);

        Signature mSignatures[] = (Signature[]) packag.getClass()
                .getField("mSignatures").get(packag);
        return mSignatures.length > 0 ? mSignatures[0] : null;
    }

    /**
     * 获得当前程序的apk的签名信息
     *
     * @return
     * @throws Exception
     */
    public Signature getApkSignature() throws Exception {
        return getApkSignature(this.getApplicationInfo().publicSourceDir);
    }

}
