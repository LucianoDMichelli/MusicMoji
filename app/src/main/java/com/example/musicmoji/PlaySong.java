package com.example.musicmoji;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;

/*
    Most of the stuff in here is all temporary just to test it.
    Need to modify for API calls.
 */
public class PlaySong extends AppCompatActivity {

    private Button playBtn;
    private SeekBar timeBar;

    TextView songTitle;
    TextView songArtist;
    TextView songAlbum;
    TextView endTime;
    TextView elapsedTime;
    TextView lyric_container;

    private static MediaPlayer mp;
    private int totalTime;

    private String Artist;

    private String Title;
    
    private String Album;

    private String preferredLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_song);

        // Initialize variables
        playBtn = (Button) findViewById(R.id.playBtn);
        songTitle = (TextView) findViewById(R.id.songTitle);
        songArtist = (TextView) findViewById(R.id.songArtist);
        songAlbum = (TextView) findViewById(R.id.songAlbum);
        endTime = (TextView) findViewById(R.id.endTime);
        elapsedTime = (TextView) findViewById(R.id.elapsedTime);
        lyric_container = (TextView) findViewById(R.id.lyric_container);


        // Get the intent and set the title and artist
        Intent intent = getIntent();
        /* Variables to keep the Artist and Title */
        Artist = intent.getStringExtra("Artist");
        Title = intent.getStringExtra("Title");
        Album = intent.getStringExtra("Album");

        songTitle.setText(Title);
        songArtist.setText(Artist);
        songAlbum.setText(Album);

        // check the song title and load the correct songs that is preloaded into the app
        switch (Title) {
            case "Boulevard of Broken Dreams":
                mp = MediaPlayer.create(this, R.raw.boulevard_of_broken_dreams);
                break;
            case "Viva La Vida":
                mp = MediaPlayer.create(this, R.raw.viva_la_vida);
                break;
            case "Halo":
                mp = MediaPlayer.create(this, R.raw.halo);
                break;
            case "Fireflies":
                mp = MediaPlayer.create(this, R.raw.fireflies);
                break;
            case "Rolling in the Deep":
                mp = MediaPlayer.create(this, R.raw.rolling_in_the_deep);
                break;
            case "Sugar":
                mp = MediaPlayer.create(this, R.raw.sugar);
                break;
            case "Royals":
                mp = MediaPlayer.create(this, R.raw.royals);
                break;
            case "Radioactive":
                mp = MediaPlayer.create(this, R.raw.radioactive);
                break;
            case "Counting Stars":
                mp = MediaPlayer.create(this, R.raw.counting_stars);
                break;
            default:
                mp = MediaPlayer.create(this, R.raw.castle_on_the_hill);
                break;
        }

        // loop through the song when finished
        mp.setLooping(true);
        // start at 0
        mp.seekTo(0);
        // get the total time of song
        totalTime = mp.getDuration();
        // set endTime
        endTime.setText(createTimeLabel(totalTime));

        // set preferredLanguage using SharedPreferences
        retrievePreferredLanguage();

        /* Referenced understanding asynch task https://www.cs.dartmouth.edu/~campbell/cs65/lecture20/lecture20.html
         *  Specifically DownloadImageFromWeb file for the demo projects */
        // Create an async task and start it
        DownloadLyricsAndTranslate lyricsDownloaded = new DownloadLyricsAndTranslate(this);

        /* Execute the async task in order to download the lyrics and fetch the translation if needed.
        an async is needed because otherwise when running and updating the UI it won't fetch the lyrics translated */
        lyricsDownloaded.execute();

        // set the movement for the scroll bar for lyric container
        lyric_container.setMovementMethod(new ScrollingMovementMethod());
//TODO (remember to take out mp.start() and out this after the other mp stuff
        // I do not remember why this is here

        //seek bar initialization and listener
        //updates seek bar by user action
        timeBar = (SeekBar) findViewById(R.id.timeBar);
        timeBar.setMax(totalTime);
        timeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mp.seekTo(progress);
                    timeBar.setProgress(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // sets the button listener to listen to user clicks
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If song is not playing, start song
                if (!mp.isPlaying()){
                    mp.start();
                    playBtn.setBackgroundResource(R.drawable.pause);
                }
                else {
                    // if song is playing, pause it
                    mp.pause();
                    playBtn.setBackgroundResource(R.drawable.play);
                }
            }
        });


        // Temp for media player
        // Create thread to update timeBar progress and the elapsed time
        Handler handler = new Handler(getMainLooper(), new TimeBarHandler());
        new Thread(() -> {
            while (mp != null) {  //isplaying
                try {
                    Message msg = new Message();
                    msg.what = mp.getCurrentPosition();
                    msg = handler.obtainMessage(msg.what);
                    msg.sendToTarget();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private class TimeBarHandler implements Handler.Callback {

        @Override
        public boolean handleMessage(@NonNull Message msg) {

            // Here you can call any UI component you want
            int currentPosition = msg.what;
            timeBar.setProgress(currentPosition);

            //Update time label
            String elapsedtime = createTimeLabel(currentPosition);
            elapsedTime.setText(elapsedtime);
            return true;
        }
    }


    private static class DownloadLyricsAndTranslate extends AsyncTask<Void, Void, Void> {

            // Keeping a weak reference in order to refer to the Text View.
            private final WeakReference<PlaySong> activityReference;

            // Constructor for this async task passing it the textview
            public DownloadLyricsAndTranslate(PlaySong playsong) {
                activityReference = new WeakReference<PlaySong>(playsong);
            }

        // Runs this thread in the background as the UI will get updated once its done.
        // Doing it like this allows the rest of the UI to load while the lyrics are generating and avoids NetworkOnMainThreadException
        @Override
        protected Void doInBackground(Void... voids) {
            PlaySong playSong = activityReference.get(); // Needed to make class static (AsyncTasks should be static or leaks could occur)
            Lyrics x = new Lyrics();
            x.lyricProcessing(playSong.Album, playSong.Title, playSong.Artist, playSong.preferredLanguage, playSong.getApplicationContext(), playSong.lyric_container);
            mp.start();
            new Handler(Looper.getMainLooper()).post(() ->
                playSong.playBtn.setBackgroundResource(R.drawable.pause));
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Void none) {
            super.onPostExecute(none);
        }

        @Override
        protected void onProgressUpdate(Void... voids) {
            super.onProgressUpdate();
        }
    }

    // Pause when home button is pressed
    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        mp.pause();
        playBtn.setBackgroundResource(R.drawable.play);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mp.release();
    }

    // Media player handler to handle the changes
    // as the thread runs to progress the seekbar
    // and update the time
//    @SuppressLint("HandlerLeak")
//    private static final Handler handler2 = new Handler() {
//        // Keeping a weak reference in order to refer to the Text View.
//        private WeakReference<PlaySong> activityReference;
//
//        // Constructor for this async task passing it the textview
//        public void handler2(PlaySong playsong) {
//            activityReference = new WeakReference<PlaySong>(playsong);
//        }
//
//        @Override
//        public void handleMessage(@NonNull Message msg) {
//            PlaySong playsong = activityReference.get();
//            int currentPosition = msg.what;
//            playsong.timeBar.setProgress(currentPosition);
//
//            //Update time label
//            String elapsedtime = playsong.createTimeLabel(currentPosition);
//            playsong.elapsedTime.setText(elapsedtime);
//        }
//    };

    // Create the label/string to set for the times
    public String createTimeLabel(int time) {
        String timelabel = "";
        int min = time / 1000 / 60;
        int sec = time / 1000 % 60;

        timelabel = min + ":";
        if (sec < 10) timelabel += "0";
        timelabel += sec;

        return timelabel;
    }

    // a single onClick function (early binding) declared in xml file
    public void goBack(View view) {
        onBackPressed();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // stop and reset song so it doesn't play in the
        // background even when you left the activity
        mp.stop();
        mp.reset();
        // go back to previous activity
        this.finish();
    }

    void retrievePreferredLanguage(){
        SharedPreferences language = getSharedPreferences("LanguageSelection", Context.MODE_PRIVATE);
        preferredLanguage = language.getString("language", "");

        String lang = language.getString("languageFull", "None");
        Toast.makeText(getApplicationContext(), "The Current Language is: " + lang, Toast.LENGTH_LONG).show();
    }

}
