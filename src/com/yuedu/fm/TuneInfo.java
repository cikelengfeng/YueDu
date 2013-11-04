package com.yuedu.fm;

import org.json.JSONObject;

import java.io.Serializable;

/**
 *
 * Created by dong on 13-9-9.
 */
public class TuneInfo implements Serializable {
    
    public final int min;
    public final int sec;
    public final String bgURL;
    public final String imgURL;
    public final String author;
    public final String title;
    public final String player;
    public final String authorURL;
    public final String sid;
    public final String mp3URL;

    public TuneInfo(int min, String bgURL, String imgURL, String author, String title, String player, int sec, String authorURL, String sid, String mp3URL) {
        this.min = min;
        this.bgURL = bgURL;
        this.imgURL = imgURL;
        this.author = author;
        this.title = title;
        this.player = player;
        this.sec = sec;
        this.authorURL = authorURL;
        this.sid = sid;
        this.mp3URL = mp3URL;
    }

    public TuneInfo(JSONObject json) {
        if (json != null) {
            min = json.optInt("min",0);
            sec = json.optInt("sec",0);
            bgURL = json.optString("bg","");
            imgURL = json.optString("img","");
            author = json.optString("author","");
            title = json.optString("title","");
            player = json.optString("player","");
            authorURL = json.optString("author_url","");
            sid = json.optString("sid","");
            mp3URL = json.optString("mp3","");
        }else {
            min = 0;
            sec = 0;
            bgURL = null;
            imgURL = null;
            author = null;
            title = null;
            player = null;
            authorURL = null;
            sid = null;
            mp3URL = null;
        }
    }
}
