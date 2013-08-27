package com.yuedu.fm;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.yuedu.R;
import com.yuedu.image.ImageCache.ImageCacheParams;
import com.yuedu.image.ImageFetcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Set;

public class MainPlayer extends FragmentActivity {

    protected static final String PLAYER_ACTIVITY_BROADCAST = "player_activity_broadcast";
    protected static final String PLAYER_ACTIVITY_BROADCAST_CATEGORY_PLAY = "player_activity_broadcast_category_play";
    protected static final String PLAYER_ACTIVITY_BROADCAST_CATEGORY_PAUSE = "player_activity_broadcast_category_pause";

    protected static final String PLAY_TUNE_INTENT_EXTRA_PATH_KEY = "player_activity_play_tune_path_key";

    private BroadcastReceiver mServiceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Set<String> categories = intent.getCategories();
            if (categories.contains(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PLAYING)) {
                setPlayButtonPlaying(true);
                Log.d("yuedu","media player is playing!!!!");
            }else if (categories.contains(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_CURRENT_POSITION)) {
                long currentPosition = intent.getLongExtra(YueduService.PLAYER_SERVICE_BROADCAST_EXTRA_CURRENT_POSITION_KEY,0);
                setCurrentPosition((int) currentPosition);
            }else if (categories.contains(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PAUSED)) {
                setPlayButtonPlaying(false);
                Log.d("yuedu","media player is paused!!!!");
            }else if (categories.contains(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_STOPPED)) {
                setPlayButtonPlaying(false);
                Log.d("yuedu","media player is stopped!!!!");
            }else if (categories.contains(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_STOP)) {
                Log.d("yuedu","media player will stop!!!!");
            }else if (categories.contains(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PLAY)) {
                Log.d("yuedu","media player will play!!!!");
            }else if (categories.contains(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PAUSE)) {
                Log.d("yuedu","media player will pause!!!!");
            }else if (categories.contains(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PREPARE)) {
                Log.d("yuedu","media player will prepare!!!!");
                setPlayButtonPlaying(true);
                showLoading();
            }else if (categories.contains(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_ERROR_OCCURRED)) {
                setPlayButtonPlaying(false);
                hideLoading();
                Toast.makeText(getApplicationContext(),intent.getStringExtra(YueduService.PLAYER_SERVICE_BROADCAST_EXTRA_ERROR_KEY),Toast.LENGTH_LONG).show();
                Log.d("yuedu","media player error occurred!!!!");
            }else if (categories.contains(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_COMPLETE)) {
                playNextTune();
                Log.d("yuedu","media player complete!!!!");
            }else if (categories.contains(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PREPARED)) {
                Log.d("yuedu","media player prepared!!!!");
                hideLoading();
            }
        }
    };

    private void showLoading() {
        Log.d("yuedu","set progress bar indeterminate!!!!");
        getmProgressBar().setIndeterminate(true);
    }

    private void hideLoading() {
        Log.d("yuedu","set progress bar determinate!!!!");
        getmProgressBar().setIndeterminate(false);
    }

    private void setCurrentPosition(int currentPosition) {
        assert currentPosition >= 0;
        getmProgressBar().setProgress(currentPosition);
        int positionInSecond = currentPosition/1000;
        int minFirstBit = positionInSecond/600;
        int minSecondBit = positionInSecond%600/60;
        int secFirstBit = positionInSecond%600%60/10;
        int secSecondBit = positionInSecond%600%60%10;
        getmPlayedTimeTextView().setText(minFirstBit +""+ minSecondBit + ":" + secFirstBit +""+ secSecondBit);
    }

    private AsyncHttpClient mClient;
    private ArrayList<JSONObject> mPlaylist;
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

    public ListView getmListView() {
        if (mListView == null) {
            mListView = (ListView) findViewById(R.id.playlist_lv);
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    setPlaylistViewVisible(false);
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
                    "image");

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
        getmListView().setAdapter(mAdapter);
    }

    private void changeCoverForTune(JSONObject tune) {
        String url = tune.optString("bg", "");
        String title = tune.optString("title", "");
        String author = tune.optString("author", "");
        String player = tune.optString("player", "");
        String info = getString(R.string.author) + author +" "+ getString(R.string.player) + player;
        getmImageFetcher().loadImage(url, getmImageView());
        getmTitleView().setText(title);
        getmInfoView().setText(info);
        getmProgressBar().setIndeterminate(false);
        getmProgressBar().setMax(getCurrentPlayingTuneDuration());
        getmProgressBar().setProgress(0);
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
            Log.w("yuedu", "Network Type Changed "+info);
            if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI && info.getState() == NetworkInfo.State.CONNECTED) {
                Log.d("yuedu","wifi is connected");
            }else {
                Log.w("yuedu","wifi is disconnected");
                Log.d("yuedu","pause playing");
                pausePlay();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_player);
        Intent intent = new Intent(getApplicationContext(), YueduService.class);
        bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
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
                changeCoverForTune(tune);
            }

            @Override
            public void onFailure(Throwable throwable, JSONObject jsonObject) {
                super.onFailure(throwable, jsonObject);
            }
        });


        getmListButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPlaylistViewVisible(!isPlaylistViewVisible());
            }
        });
        getmPlayButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playButtonIsPlayingState()) {
                    setPlayButtonPlaying(false);
                    pausePlay();
                }else {
                    setPlayButtonPlaying(true);
                    play();
                }
            }
        });
        getmNextButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNextTune();
            }
        });
        getmTitleView().setSelected(true);
    }

    private void setPlaylistViewVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        getmListViewContainer().setVisibility(visibility);
    }

    private boolean isPlaylistViewVisible() {
        return getmListViewContainer().getVisibility() == View.VISIBLE;
    }

    private boolean playButtonIsPlayingState() {
        return getmPlayButton().isSelected();
    }

    private void setPlayButtonPlaying(boolean isPlaying) {
        getmPlayButton().setSelected(isPlaying);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
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
            getmListView().setSelection(index);
            JSONObject tune = getmPlaylist().get(index);
            changeCoverForTune(tune);
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
            setPlayButtonPlaying(true);
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
        filter.addCategory(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_ERROR_OCCURRED);
        filter.addCategory(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PLAYING);
        filter.addCategory(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PREPARED);
        filter.addCategory(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PREPARE);
        filter.addCategory(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_STOPPED);
        filter.addCategory(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_PAUSED);
        filter.addCategory(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_STOP);
        filter.addCategory(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PAUSE);
        filter.addCategory(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_WILL_PLAY);
        filter.addCategory(YueduService.PLAYER_SERVICE_BROADCAST_CATEGORY_PLAYER_COMPLETE);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mServiceBroadcastReceiver,filter);
    }

    private void unregisterLocalBroadcastReceiver() {
        assert mServiceBroadcastReceiver != null;
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mServiceBroadcastReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_player, menu);
        return false;
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
            String infoStr = getContext().getString(R.string.author) + author +" "+getContext().getString(R.string.player) + player;
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
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
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

}
