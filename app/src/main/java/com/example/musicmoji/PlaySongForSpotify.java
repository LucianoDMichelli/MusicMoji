package com.example.musicmoji;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.types.Track;

import java.io.IOException;
import java.lang.ref.WeakReference;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.example.musicmoji.Main_Activity_Page.isPlaying;
import static com.example.musicmoji.Main_Activity_Page.playerApi;

/*
    This class is similar to play song but with some modified methods specially for spotify.
    Mainly, instead of using media player to play local songs, here we use spotify remote API and web APIs
    to control progress of currently playing song.
 */
public class PlaySongForSpotify extends AppCompatActivity {

    private Button playBtn;
    private SeekBar timeBar;

    TextView songTitle;
    TextView songArtist;
    TextView songAlbum;
    TextView endTime;
    TextView elapsedTime;
    TextView lyric_container;


    private int currentPosition;
    private SharedPreferences mSharedPreferences;
    private String deviceId;

    private Thread timebarThread;

    private static final String CLIENT_ID = ApiKeys.spotifyClientID;
    private static final String REDIRECT_URI = "http://musicmoji.com/callback/";

    private String Artist;

    private String Title;

    private String Album;

    private String preferredLanguage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_song_for_spotify);



        // Initialize variables
        playBtn = (Button) findViewById(R.id.playBtnForSpotify);
        songTitle = (TextView) findViewById(R.id.songTitleForSpotify);
        songArtist = (TextView) findViewById(R.id.songArtistForSpotify);
        songAlbum = (TextView) findViewById(R.id.songAlbumForSpotify);
        endTime = (TextView) findViewById(R.id.endTimeForSpotify);
        elapsedTime = (TextView) findViewById(R.id.elapsedTimeForSpotify);
        lyric_container = (TextView) findViewById(R.id.lyric_containerForSpotify);

        // Get the intent and set the title, artist, album, and duration
        Intent intent = getIntent();

        /* Variables to keep the Artist and Title */
        Artist = intent.getStringExtra("Artist");
        Title = intent.getStringExtra("Title");
        Album = intent.getStringExtra("Album");
        //log.v("help", Artist + Title + Album);

        songTitle.setText(Title);
        songArtist.setText(Artist);
        songAlbum.setText(Album);

        int totalTime = Integer.parseInt(intent.getStringExtra("duration_ms"));

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

        // set endTime
        endTime.setText(createTimeLabel(totalTime));


        //seek bar initialization and listener
        //updates seek bar by user action
        timeBar = (SeekBar) findViewById(R.id.timeBarForSpotify);
        timeBar.setMax(totalTime);


        timeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentPosition = progress;
                    playerApi.seekTo(currentPosition);
                    timeBar.setProgress(currentPosition);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // the pause/play button click listener
        playBtn.setOnClickListener( v -> {
            if (isPlaying) {
                pauseMusic();
            } else {
                playMusic();

            }
        });


        // For updating timeBar
        Handler handler = new Handler(getMainLooper(), new TimeBarHandler());
        new Thread(() -> {
            while (playerApi != null) {
                try {
                    playerApi.getPlayerState().setResultCallback(playerState -> {
                        currentPosition = (int) playerState.playbackPosition;
                        if (totalTime - currentPosition <= 1) {
                            playerApi.seekTo(0);
                            currentPosition = 0;
                        }
                    });

                    Message msg = new Message();
                    msg.what = currentPosition;
                    msg = handler.obtainMessage(msg.what);
                    msg.sendToTarget();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    //-----------------------------------------------------------------------------------------------------------------------------

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

    // Create the label/string to set for the times
    public String createTimeLabel ( int time){
        String timelabel;
        int min = time / 1000 / 60;
        int sec = time / 1000 % 60;

        timelabel = min + ":";
        if (sec < 10) timelabel += "0";
        timelabel += sec;

        return timelabel;
    }

//-----------------------------------------------------------Spotify Connection and "play & pause" Methods---------------------------------------------------
// This method starts the Spotify Connection by opening up
// the connection once user is authenticated
    @Override
    protected void onStart() {
        super.onStart();
        // We will start writing our code here.

        // Set the connection parameters
        ConnectionParams connectionParams =
            new ConnectionParams.Builder(CLIENT_ID)
                    .setRedirectUri(REDIRECT_URI)
                    .showAuthView(true)
                    .build();

        // Connect to Spotify using the connection parameters and
        // set a Connection listener and implement the ConnectionListener
        // methods. Handles connection failures and success
        SpotifyAppRemote.connect(this, connectionParams,
            new Connector.ConnectionListener() {

                @Override
                public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                    playerApi = spotifyAppRemote.getPlayerApi();

                    // Now you can start interacting with App Remote

                    playerApi
                            .subscribeToPlayerState()
                            .setEventCallback(playerState -> {
                                final Track track = playerState.track;
                                if (track != null) {
                                    isPlaying = !(playerState.isPaused);
                                }
                            });
                }

                @Override
                public void onFailure(Throwable throwable) {
                    Log.e("MainActivity", throwable.getMessage(), throwable);

                    // Something went wrong when attempting to connect! Handle errors here
                }
            });
    }
    
    protected  void pauseMusic() {
        playerApi.pause();
        playBtn.setBackgroundResource(R.drawable.play);
//        Log.e("play song", "Spotify is playing and now paused!");
    }
    
    protected void playMusic() {
//        Log.e("play song", "Spotify is paused and now playing!");
        playerApi.resume();
        playBtn.setBackgroundResource(R.drawable.pause);
    }


//-----------------------------------------------------------Methods of seeking to position and controlling playing progress ----------------------------------------

    private void SeekToPositionInCurrentlyPlayingTrack (int progress){
        //need to get play list first, temporary
        String url = "https://api.spotify.com/v1/me/player/seek?position_ms=29000&device_id=e7de3c9f2f4f04bb7107c33b805bda25a9fbed70";
        String token = mSharedPreferences.getString("token", "");
        OkHttpClient okHttpClient = new OkHttpClient();
        final Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .put(null)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();
        Call call = okHttpClient.newCall(request);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("Spotify Info", "onFailure: Get Playlist failed.");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String res = response.body().string();
            }
        });
    }

    private void getActiveDevice () {
        String url = "https://api.spotify.com/v1/me/player/devices";
        String token = mSharedPreferences.getString("token", "");
        OkHttpClient okHttpClient = new OkHttpClient();
        final Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("Spotify Info", "onFailure: Get Playlist failed.");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String res = response.body().string();
                JSONObject deviceJSON = JSONObject.parseObject(res);
                getDeviceInfo(deviceJSON);
            }
        });
    }

    private void getDeviceInfo (JSONObject deviceJSON) {
        JSONArray devices = deviceJSON.getJSONArray("devices");
        for (int i = 0; i < devices.size(); i++) {
            JSONObject device = devices.getJSONObject(i);
            boolean isActive = Boolean.parseBoolean(device.getString("is_active"));
            if (isActive)
                deviceId = device.getString("id");
        }
    }
    //-----------------------------------------------------------ending Methods---------------------------------------------------
    // a single onClick function (early binding) declared in xml file
    public void goBack (View view){
        // stop and reset song so it doesn't play in the
        // background even when you left the activity
        // go back to previous activity
        onBackPressed();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        pauseMusic();
        this.finish();
    }

    // Needed to make class static (AsyncTasks should be static or leaks could occur)
    private static class DownloadLyricsAndTranslate extends AsyncTask<Void, Void, Void> {

        // Keeping a weak reference in order to refer to the Text View.
        private final WeakReference<PlaySongForSpotify> activityReference;

        // Constructor for this async task passing it the textview
        public DownloadLyricsAndTranslate(PlaySongForSpotify playsongforspotify) {
            activityReference = new WeakReference<PlaySongForSpotify>(playsongforspotify);
        }

        // Runs this thread in the background as the UI will get updated once its done.
        // Doing it like this allows the rest of the UI to load while the lyrics are generating and avoids NetworkOnMainThreadException
        @Override
        protected Void doInBackground(Void... voids) {
            PlaySongForSpotify playSongForSpotify = activityReference.get();
            Lyrics lyrics = new Lyrics();
            lyrics.lyricProcessing(playSongForSpotify.Album, playSongForSpotify.Title, playSongForSpotify.Artist, playSongForSpotify.preferredLanguage, playSongForSpotify.getApplicationContext(), playSongForSpotify.lyric_container);
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            PlaySongForSpotify playSongForSpotify = activityReference.get();
            playSongForSpotify.pauseMusic();
        }

        @Override
        protected void onPostExecute(Void none) {
            super.onPostExecute(none);
            PlaySongForSpotify playSongForSpotify = activityReference.get(); // Have to re-declare this in every stage or context could leak
            playSongForSpotify.playMusic();

        }

        @Override
        protected void onProgressUpdate(Void... voids) {
            super.onProgressUpdate();
        }
    }

    // Pause when user leaves the screen
    @Override
    protected void onPause() {
        super.onPause();
        pauseMusic();
    }

    void retrievePreferredLanguage(){
        SharedPreferences language = getSharedPreferences("LanguageSelection", Context.MODE_PRIVATE);
        preferredLanguage = language.getString("language", "");

        String lang = language.getString("languageFull", "None");
        Toast.makeText(getApplicationContext(), getString(R.string.selected_language) + lang, Toast.LENGTH_SHORT).show();
    }

}




