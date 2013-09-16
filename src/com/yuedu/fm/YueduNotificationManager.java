package com.yuedu.fm;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import com.yuedu.R;

/**
 * Created by dong on 13-9-12.
 */
public enum YueduNotificationManager {
    SINGLE_INSTANCE;

    private static final int ONGOING_NOTIFICATION_ID = 0x77<<7;

    public void setPlayButtonPlaying(Context context ,boolean b) {
        PendingIntent pendingIntent = getPendingIntent(context);

        RemoteViews remoteViews = new RemoteViews(context.getPackageName(),R.layout.notification_layout);
        int res = b ? R.drawable.pause : R.drawable.play;
        remoteViews.setInt(R.id.notification_pause_or_play,"setImageResource",res);

        setRemoteActions(context, remoteViews);

        notify(context, getNotification(context, pendingIntent, remoteViews));
    }

    public void updateTitle(Context context) {
        PendingIntent pendingIntent = getPendingIntent(context);

        RemoteViews remoteViews = new RemoteViews(context.getPackageName(),R.layout.notification_layout);
        remoteViews.setCharSequence(R.id.notification_tune_name, "setText",DataAccessor.SINGLE_INSTANCE.getPlayingTune().title);

        setRemoteActions(context, remoteViews);

        notify(context,getNotification(context,pendingIntent,remoteViews));
    }

    public void showForegroundNotification(Service service) {
        PendingIntent pendingIntent = getPendingIntent(service);

        RemoteViews remoteViews = new RemoteViews(service.getPackageName(),R.layout.notification_layout);
        remoteViews.setCharSequence(R.id.notification_tune_name, "setText",DataAccessor.SINGLE_INSTANCE.getPlayingTune().title);

        setRemoteActions(service, remoteViews);

        service.startForeground(ONGOING_NOTIFICATION_ID, getNotification(service,pendingIntent,remoteViews));

    }

    private void setRemoteActions(Context context, RemoteViews remoteViews) {
        Intent nextIntent = new Intent(YueduService.PLAYER_RECEIVING_BROADCAST_ACTION);
        nextIntent.addCategory(YueduService.PLAYER_RECEIVING_BROADCAST_CATEGORY_PLAY_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(context, 0, nextIntent, 0);
        Intent switchIntent =  new Intent(YueduService.PLAYER_RECEIVING_BROADCAST_ACTION);
        switchIntent.addCategory(YueduService.PLAYER_RECEIVING_BROADCAST_CATEGORY_SWITCH_PLAYSTATE);
        PendingIntent switchPendingIntent = PendingIntent.getBroadcast(context, 0, switchIntent, 0);
        remoteViews.setOnClickPendingIntent(R.id.notification_next,nextPendingIntent);
        remoteViews.setOnClickPendingIntent(R.id.notification_pause_or_play,switchPendingIntent);
    }

    private void notify(Context context,Notification notification) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(ONGOING_NOTIFICATION_ID,notification);
    }

    private PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context,MainPlayer.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
        return pendingIntent;
    }

    private Notification getNotification(Context context,PendingIntent contentIntent,RemoteViews content) {
        return  new NotificationCompat.Builder(context).setContentIntent(contentIntent).setSmallIcon(R.drawable.ic_launcher).setContent(content).build();
    }

    public void stopForeground(Service service) {
        service.stopForeground(true);
    }

}