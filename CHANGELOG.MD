# Since initial submission

* Translated lyrics are now red (previously were just surrounded by curly braces)
* Lyrics and translations are now saved to the device after generating, so they will not have to be re-generated on subsequent playbacks of a song
* Tweaked wording of language preference page to make it more clear
* Added album names
* Moved lyricProcessing() and getLyrics() to their own class file to avoid redundancy
* Added ApiKeys class file to store keys for ease of use
* Switched to Happi.Dev API for lyrics
* Fixed Spotify seek bar
* Fixed goBack buttons (black arrow in top left corner of Song pages)
* Fixed potential Handler leak in PlaySong seekbar (the part that updates current timestamp)
* Fixed Spotify pausing (initial pause and pauses when leaving activity)
* Fixed pause/play icons not updating on initial pause while waiting for lyrics
* Added offline mode (allows users to listen to Server Songs without connecting to Spotify or having an internet connection)
* Updated app icon to use the logo on the start screen instead of Android default
* Fixed issue resulting from lyric API sometimes returning "??" as song language
* Various other fixes and code cleanup
