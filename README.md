# MusicMoji
Reads lyrics of selected song and displays them for the user with emojis inserted

Allows users to play music through Spotify using their Recently Played playlist, or by using the search function

Also comes pre-loaded with 10 songs, in the section labelled "Server Songs"

Originally a school group project

**Demo:** https://youtu.be/jpO03njJhgI

![translated to Portuguese](https://i.gyazo.com/95658ed53697893ef7f5e09cd3bd049d.png)

## My Contributions

* Wrote lyricProcessing() in Lyrics
  * Takes song lyrics and reads through word-by-word to generate appropriate emojis
    * Performs various checks to account for words associated with emojis having different forms/tenses (e.g. sings -> sing, smiled -> smile)
  * In addition to being displayed in the original language, the user can choose one of 5 languages to translate the lyrics to: English, Spanish, Portuguese, French, and Chinese (Simplified)
    * Translated lyrics will be under each line of the original language
    * Translated lyrics will contain emojis
  * Implemented lyric saving so that songs only have to be emojified/translated for a given language once
* Co-wrote getLyrics() in Lyrics
  * Parsed lyric API response and added necessary formatting to lyric string
  * Updated lyric API after original was deprecated -> made necessary changes to API call and parsing
* Co-wrote DownloadLyricsAndTranslate in PlaySong and PlaySongForSpotify
  * Got class working so that getLyrics() and lyricProcessing() work asynchronously
    * When a song is selected, the Song screen appears with a message telling the user that emojis are being generated, and the music is paused. When they are ready they will replace the message and the song will start playing.
* Fixed bugs, cleaned up code, and added intended features according to professor feedback after app presentation

## APIs/Libraries used

* IBM Watson Language Translator -> https://cloud.ibm.com/catalog/services/language-translator
* Happi.dev (formerly Apiseeds) Music API -> https://happi.dev/docs/music
* Spotify Android SDK -> https://developer.spotify.com/documentation/android/
* emoji-java by vdurmont -> https://github.com/vdurmont/emoji-java

## Notes

Spotify must be installed and you must have a premium account for the app to work (free accounts can only use shuffle play, so the songs that users select will not actually be the ones that are played). The app also cannot be in offline mode

You will need developer keys for the language translator, lyric API, and Spotify. Spotify is free, the other two have free versions

The language translator allows 1,000,000 translated characters per month

The lyric api allows 8,000 calls per month (effectively 4,000 as 2 calls are needed to find the song and then retrieve the lyrics)

For Spotify, you will need to [register your app](https://developer.spotify.com/documentation/general/guides/app-settings/#register-your-app). You will also need to add the package name (com.example.musicmoji) and your [SHA-1 fingerprint](https://stackoverflow.com/questions/27609442/how-to-get-the-sha-1-fingerprint-certificate-in-android-studio-for-debug-mode) to your app settings in the Spotify dashboard

## Known Bugs/Issues
* There is a slight delay when pausing/unpausing Spotify songs (possibly an issue with the Spotify SDK)
* Autoplay for Spotify songs does not work if saved lyrics are retrieved
* Spotify authentication is required to use the app (even if user just wants to listen to the pre-loaded songs). User also has to authenticate each time they use the app, so Spotify needs to be in online mode and the user has to have an internet connection (again, even if they only want to listen to the pre-loaded songs)
