package com.example.musicmoji;

public class ServerSongsData {

    public String title;
    public String artist;
    public String album;

    public ServerSongsData(String title, String artist, String album) {
        this.title = title;
        this.artist = artist;
        this.album = album;
    }

    public ServerSongsData(){}

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() { return album; }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setAlbum(String album) { this.album = album; }
}
