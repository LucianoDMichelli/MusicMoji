package com.example.musicmoji;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.spotify.protocol.types.Track;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.example.musicmoji.Main_Activity_Page.mSpotifyAppRemote;

// Similar to ServerSongFragment but modified for
// Spotify songs from API call
// List View is set up just need to add the song titles and artists
// to the ArrayList before setAdapter, or if necessary add and then
// call adapter.notifyDataSetChanged();
// refer to ServerSongFragment for more detail
public class SpotifySongFragment extends Fragment {

    // Here to set up listview with Spotify Songs
    ListView list;
    public ArrayList<String> titles = new ArrayList<String>();
    public ArrayList<String> artists = new ArrayList<String>();
    public ArrayList<String> albums = new ArrayList<String>();

    private SharedPreferences mSharedPreferences;
    private JSONObject playlistJSON;
    private List<List<String>> songsInfo;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_spotify_song, container, false);

        mSharedPreferences = Objects.requireNonNull(getActivity()).getSharedPreferences("SPOTIFY", 0);

        songsInfo = new ArrayList<>();

        getPlaylistFromSpotify();

        // EXAMPLE of adding to ArrayList
//        titles.add("Laputa, Castle in the Sky");
//        artists.add("Joe Hisashi");

        // get the listview we want to customize
        list = (ListView) view.findViewById(R.id.spotify_listview);

        // handle item clicks
        // currently show toast on click
        list.setOnItemClickListener((AdapterView.OnItemClickListener) (parent, view1, position, id) -> {

            TextView getTitle = (TextView) view1.findViewById(R.id.tv_title);
            TextView getArtist = (TextView) view1.findViewById(R.id.tv_artist);
            TextView getAlbum = (TextView) view1.findViewById(R.id.tvAlbum);

            String strTitle = getTitle.getText().toString().trim();
            String strArtist = getArtist.getText().toString().trim();
            String strAlbum = getAlbum.getText().toString().trim();
            String trackID = songsInfo.get(position).get(0);

    // pass the information of current playing song to play song page.
            Intent intent = new Intent(getActivity(), PlaySongForSpotify.class);
            intent.putExtra("Title", strTitle);
            intent.putExtra("Artist", strArtist);
            intent.putExtra("Album", strAlbum);
            intent.putExtra("duration_ms", songsInfo.get(position).get(4));
            startActivity(intent);
            connected(trackID);
        });

        return view;
    }
    //----------------------------------------------------------- Method to extracting tracks' information from JSON file-------------------------
    private void getSongsInfo() {
        JSONArray items = playlistJSON.getJSONArray("items");
        for (int i = 0; i < items.size(); i++) {
            List<String> songInfo = new ArrayList<>();
            JSONObject songInfoJSON = items.getJSONObject(i);
            JSONObject trackJSON = songInfoJSON.getJSONObject("track");
            String name = trackJSON.getString("name");
            String id = trackJSON.getString("id");
            String album = trackJSON.getJSONObject("album").getString("name");
            String duration_ms = trackJSON.getString("duration_ms");
            JSONArray artistsJSON = trackJSON.getJSONArray("artists");
            StringBuilder artistsName = new StringBuilder();
            for (int j = 0; j < artistsJSON.size() - 1; j++) {
                JSONObject artistInfo = artistsJSON.getJSONObject(j);
                String artistName = artistInfo.getString("name");
                artistsName.append(artistName);
                artistsName.append("; ");
            }
            JSONObject artistInfo = artistsJSON.getJSONObject(artistsJSON.size() - 1);
            String artistName = artistInfo.getString("name");
            artistsName.append(artistName);
            String artist = artistsName.toString();
            songInfo.add(id);
            songInfo.add(name);
            songInfo.add(artist);
            songInfo.add(album);
            songInfo.add(duration_ms);
            songsInfo.add(songInfo);
            System.out.println(name);
            titles.add(name);
            artists.add(artist);
            albums.add(album);
        }
        // here you check the value of getActivity() and break up if needed
        if(getActivity() == null)
            return;

        getActivity().runOnUiThread(() -> {
        // create instance of class CustomServerAdapter and pass in the
        // activity, the titles, and artists that our custom view wants to show
        CustomSpotifyAdapter adapter = new CustomSpotifyAdapter(getActivity(), titles, artists, albums);
        // set adapter to list
        list.setAdapter(adapter);
        }
        );
    }

    //----------------------------------------------------------- Method to get playlist from spotify and get a JSON file------------------------------

    // get playlist from spotify
    private void getPlaylistFromSpotify() {
        //need to get play list first, temporary
        String url = "https://api.spotify.com/v1/me/player/recently-played";
        // url = "https://api.spotify.com/v1/me/top/tracks?limit=20";
        //String url = "https://api.spotify.com/v1/me/tracks?market=US&limit=30";
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
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(getContext(), getContext().getString(R.string.SpotifySongFragment_couldnotretrieve), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                assert response.body() != null;
                String res = response.body().string();
                playlistJSON = JSONObject.parseObject(res);
                getSongsInfo();
            }
        });
    }

    // Custom List View Adapter to show custom list view
    // extends the ArrayAdapter
    class CustomSpotifyAdapter extends ArrayAdapter {

        // The constructor for the adapter
        CustomSpotifyAdapter(Context c, ArrayList<String> titles, ArrayList<String> artists, ArrayList<String> albums) {
            super(c, R.layout.list_item, R.id.tv_title, titles);

        }

        // gets the view and fills it in with custom information
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = ((Activity) getContext()).getLayoutInflater().inflate(R.layout.list_item, null);

            // finds the text view by id using View v as reference
            TextView title = (TextView) v.findViewById(R.id.tv_title);
            TextView artist = (TextView) v.findViewById(R.id.tv_artist);
            TextView album = (TextView) v.findViewById(R.id.tvAlbum);


            // sets the text with custom information passed in during instantiation
            title.setText(titles.get(position));
            artist.setText(artists.get(position));
            album.setText(albums.get(position));
            // returns the view
            return v;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        MyBroadcastReceiver mBroadcastReceiver = new MyBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MyBroadcastReceiver.BroadcastTypes.METADATA_CHANGED);
        intentFilter.addAction(MyBroadcastReceiver.BroadcastTypes.PLAYBACK_STATE_CHANGED);
        intentFilter.addAction(MyBroadcastReceiver.BroadcastTypes.QUEUE_CHANGED);
        intentFilter.addAction(MyBroadcastReceiver.BroadcastTypes.SPOTIFY_PACKAGE);
        Objects.requireNonNull(getActivity()).getApplicationContext().registerReceiver(mBroadcastReceiver, intentFilter);
    }

//user authorization and connect our app with spotify
    private void connected(String trackID) {
        mSpotifyAppRemote.getPlayerApi().play("spotify:track:"+ trackID);

        // Subscribe to PlayerState
        mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerState()
                .setEventCallback(playerState -> {
                    final Track track = playerState.track;
                    if (track != null) {
                        Main_Activity_Page.isPlaying = !(playerState.isPaused);
                    }
                });
    }


}

