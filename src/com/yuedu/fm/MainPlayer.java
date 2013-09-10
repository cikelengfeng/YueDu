package com.yuedu.fm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
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

import com.yuedu.R;
import com.yuedu.image.ImageCache.ImageCacheParams;
import com.yuedu.image.ImageFetcher;

import java.util.List;
import java.util.Set;

public class MainPlayer extends FragmentActivity {


    private BroadcastReceiver mServiceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Set<String> categories = intent.getCategories();
            if (YueduService.PLAYER_SENDING_BROADCAST_ACTION.equals(action)) {
                if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_PLAYING)) {
                    setPlayButtonPlaying(true);
                    Log.d("yuedu","media player is playing!!!!");
                }else if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_CURRENT_POSITION)) {
                    long currentPosition = intent.getLongExtra(YueduService.PLAYER_SERVICE_BROADCAST_EXTRA_CURRENT_POSITION_KEY,0);
                    setCurrentPosition((int) currentPosition);
                }else if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_PAUSED)) {
                    setPlayButtonPlaying(false);
                    Log.d("yuedu","media player is paused!!!!");
                }else if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_STOPPED)) {
                    setPlayButtonPlaying(false);
                    Log.d("yuedu","media player is stopped!!!!");
                }else if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_WILL_STOP)) {
                    Log.d("yuedu","media player will stop!!!!");
                }else if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_WILL_PLAY)) {
                    updateCover();
                    Log.d("yuedu","media player will play!!!!");
                }else if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_WILL_PAUSE)) {
                    Log.d("yuedu","media player will pause!!!!");
                }else if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_WILL_PREPARE)) {
                    Log.d("yuedu","media player will prepare!!!!");
                    setPlayButtonPlaying(true);
                    showLoading();
                }else if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_ERROR_OCCURRED)) {
                    setPlayButtonPlaying(false);
                    hideLoading();
                    Toast.makeText(getApplicationContext(),intent.getStringExtra(YueduService.PLAYER_SERVICE_BROADCAST_EXTRA_ERROR_KEY),Toast.LENGTH_LONG).show();
                    Log.d("yuedu","media player error occurred!!!!");
                }else if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_COMPLETE)) {
                    Log.d("yuedu","media player complete!!!!");
                }else if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_PREPARED)) {
                    Log.d("yuedu","media player prepared!!!!");
                    hideLoading();
                }else if (categories.contains(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_STATE_REPORT)) {
                    boolean isPlaying = intent.getBooleanExtra(YueduService.PLAYER_SERVICE_BROADCAST_EXTRA_PLAYSTATE_KEY,false);
                    Log.d("yuedu","media player state report " + isPlaying +" !!!!");
                    setPlayButtonPlaying(isPlaying);
                }
            }else if (DataAccessor.DATA_ACCESSOR_DOWNLOAD_COMPLETE_ACTION.equals(action)) {
                Log.d("yuedu","data list download complete!!!!");
                updateUI();
            }else if (DataAccessor.DATA_ACCESSOR_DOWNLOAD_FAILED_ACTION.equals(action)) {
                Log.d("yuedu","data list download failed!!!!");
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
        TuneInfo tune = DataAccessor.SINGLE_INSTANCE.getPlayingTune();
        int min = tune.min;
        int sec = tune.sec;
        return (min * 60 + sec)*1000;
    }

    public ListView getmListView() {
        if (mListView == null) {
            mListView = (ListView) findViewById(R.id.playlist_lv);
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    setPlaylistViewVisible(false);
                    playTuneAtIndex(position);
                }
            });
        }
        return mListView;
    }

    public RelativeLayout getmListViewContainer() {
        if (mListViewContainer == null) {
            mListViewContainer = (RelativeLayout) findViewById(R.id.playlist_ll);
        }
        return mListViewContainer;
    }

    public PlaylistAdapter getAdapter() {
        if (mAdapter == null) {
            mAdapter = new PlaylistAdapter(getApplicationContext(), DataAccessor.SINGLE_INSTANCE.getDataList(), getmImageFetcher());
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

    private void updateCover() {
        TuneInfo tune = DataAccessor.SINGLE_INSTANCE.getPlayingTune();
        String url = tune.bgURL;
        String title = tune.title;
        String author = tune.author;
        String player = tune.player;
        String info = getString(R.string.author) + author +" "+ getString(R.string.player) + player;
        getmImageFetcher().loadImage(url, getmImageView());
        getmTitleView().setText(title);
        getmInfoView().setText(info);
        getmProgressBar().setIndeterminate(false);
        getmProgressBar().setMax(getCurrentPlayingTuneDuration());
        getmProgressBar().setProgress(0);
        getmPlayedTimeTextView().setText("00:00");
    }

    private void updateListView() {
        getmListView().setAdapter(getAdapter());
    }

    private void updateUI() {
        updateCover();
        updateListView();
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
        startService(intent);
        registerLocalBroadcastReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mNetworkStateReceiver, filter);

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
        if (DataAccessor.SINGLE_INSTANCE.getDataList().size() > 0) {
            updateUI();
        }
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
        unregisterLocalBroadcastReceiver();
    }

    private void play() {
        Intent intent = new Intent(YueduService.PLAYER_RECEIVING_BROADCAST_ACTION);
        intent.addCategory(YueduService.PLAYER_RECEIVING_BROADCAST_CATEGORY_PLAY);
        sendLocalBroadcast(intent);
        setPlayButtonPlaying(true);
    }

    private void pausePlay() {
        Intent intent = new Intent(YueduService.PLAYER_RECEIVING_BROADCAST_ACTION);
        intent.addCategory(YueduService.PLAYER_RECEIVING_BROADCAST_CATEGORY_PAUSE);
        sendLocalBroadcast(intent);
    }

    private void playNextTune() {
        DataAccessor.SINGLE_INSTANCE.playNextTune();
        play();
    }

    private void playTuneAtIndex(int index) {
        DataAccessor.SINGLE_INSTANCE.playTuneAtIndex(index);
        play();
    }

    private void sendLocalBroadcast(Intent intent) {
        assert intent != null;
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private void registerLocalBroadcastReceiver() {
        assert mServiceBroadcastReceiver != null;
        IntentFilter filter = new IntentFilter(YueduService.PLAYER_SENDING_BROADCAST_ACTION);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_CURRENT_POSITION);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_ERROR_OCCURRED);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_PLAYING);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_PREPARED);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_WILL_PREPARE);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_STOPPED);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_PAUSED);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_WILL_STOP);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_WILL_PAUSE);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_WILL_PLAY);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_COMPLETE);
        filter.addCategory(YueduService.PLAYER_SENDING_BROADCAST_CATEGORY_PLAYER_STATE_REPORT);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mServiceBroadcastReceiver,filter);
        IntentFilter dataReceivedFilter = new IntentFilter(DataAccessor.DATA_ACCESSOR_DOWNLOAD_COMPLETE_ACTION);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mServiceBroadcastReceiver, dataReceivedFilter);
        IntentFilter dataFailedFilter = new IntentFilter(DataAccessor.DATA_ACCESSOR_DOWNLOAD_FAILED_ACTION);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mServiceBroadcastReceiver,dataFailedFilter);

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

    static private class PlaylistAdapter extends ArrayAdapter<TuneInfo> {

        private LayoutInflater inflater;
        private ImageFetcher imageFetcher;

        public PlaylistAdapter(Context context, List<TuneInfo> list, ImageFetcher fetcher) {
            super(context, 0, list);
            inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            imageFetcher = fetcher;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TuneInfo tune = getItem(position);
            String url = tune.imgURL;
            String titleStr = tune.title;
            String author = tune.author;
            String player = tune.player;
            String infoStr = getContext().getString(R.string.author) + author +" "+getContext().getString(R.string.player) + player;
            String timeStr = tune.min + ":" + tune.sec;
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
