package com.example.musicmoji;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.text.HtmlCompat;

// IBM Watson Translation API
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.language_translator.v3.LanguageTranslator;
import com.ibm.watson.language_translator.v3.model.TranslateOptions;

// Emoji library
// https://github.com/vdurmont/emoji-java
import com.vdurmont.emoji.Emoji;
import com.vdurmont.emoji.EmojiManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Lyrics {

    private String lyrics;
    private String lyricsLanguageTag;
    private boolean retried = false;

    /* Referenced https://www.vogella.com/tutorials/JavaLibrary-OkHttp/article.html to understand synchronous api calls*/
    private void getLyrics(String Artist, String Title, String Album) {

        // Creating an client for requesting lyrics from Happi.dev (formerly Apiseeds)
        OkHttpClient client = new OkHttpClient();

        String apiKey = ApiKeys.happiDevKey;

        // Forming the URL for calling the API call.
        String url = "https://api.happi.dev/v1/music?q=" + Title + " " + Artist + "&limit=5&apikey=" + apiKey + "&type=track&lyrics=1";

        // Buidling the Request
        Request request = new Request.Builder()
                .url(url)
                .build();
        // This will make an api request synchronously, the try catch block is ued for IOExeceptions if there is an IO error.
        try {
            // The response from the api after making the call.
            Response response = client.newCall(request).execute();
            // Json from the response from the api call.
            String result = response.body().string();
            try {
                // Parsing the JSON Object
                JSONObject obj = new JSONObject(result);
                JSONArray resultArray = obj.getJSONArray("result");
                // Parsed JSON gets the lyrics url and language tag
                lyricsLanguageTag =  resultArray.getJSONObject(0).getString("lang");

                int numResults = obj.getInt("length");
                String temp = "";
                for (int i=0; i < numResults; i++) {
                    JSONObject obj2 = resultArray.getJSONObject(i);
                    // Need to make sure artist and track names don't include the features
                    String track = obj2.getString("track");
                    if (track.contains("feat.") || Title.contains("ft.") || Title.contains("(with")) {
                        track = track.substring(0, track.indexOf('('));
                    }
                    if (obj2.getString("artist").split(";")[0].equals(Artist) && track.equals(Title)) {
                        temp = obj.getJSONArray("result").getJSONObject(i).getString("api_lyrics");
                        if (obj2.getString("album").equals(Album)) {
                            lyrics = obj.getJSONArray("result").getJSONObject(i).getString("api_lyrics");
                            break;
                        }
                    }
                    // Will either be a song with a matching title and artist (but different album) or an empty string
                    lyrics = temp;
                }
            } catch (JSONException e) {
                // Failed to get the lyrics of the song
                lyrics = "";
                lyricsLanguageTag = "";
                e.printStackTrace();
            }

            // Have to make a second api call to get lyrics
            if (!lyrics.equals("")) {
                url = lyrics + "?apikey=" + apiKey;

                request = new Request.Builder()
                        .url(url)
                        .build();
                try {
                    // The response from the api after making the call.
                    response = client.newCall(request).execute();
                    // Json from the response from the api call.
                    result = response.body().string();
                    try {
                        // Parsing the JSON Object
                        JSONObject obj = new JSONObject(result);
                        // Parsed JSON gets the lyrics and language tag
                        // .replace is to make it parsable in lyricsProcessing()
                        // * at the beginning is to make spacing consistent (there is a space in front of all other lines, likely due to adding asterisks in the .replace)
                        lyrics = "*" + spacePunctuation(obj.getJSONObject("result").getString("lyrics"));
                    } catch (JSONException e) {
                        // Failed to get the lyrics of the song
                        lyrics = "";
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void lyricProcessing(String Album, String Title, String Artist, String preferredLanguage, Context context, TextView lyric_container) {
        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
        // As Easy As 1-2-3-4-5-6

        // Setup
        // 1. Read each line (translate if need be)
        // 2. Read each word (translate if need be)
        // 3. Convert lyrics to emojis (includes sample emoji JSON object)
        // 3a. Manually check for certain emojis (exceptions to check rules)
        // 3b. Try matching for alias
        // 3c. Try matching for tags
        // 4. Checks -> Check if word is a different form of an alias/tag (plural, -ing, etc.)
        // 4a. Modify the lyric using checks, add to an ArrayList
        // 4b. Go through the check variations and try to find a match
        // 5. Add the processed line to emojiLyrics (translate if need be)
        // 6. Add emojified/translated lyrics to lyric_container
        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */


// Setup
// Set variables, find early problems, set up translator

        // Songs with features are not always processed correctly by the API search
        Artist = Artist.split(";")[0];
        if (Title.contains("feat.") || Title.contains("ft.") || Title.contains("(with")) {
            Title = Title.substring(0, Title.lastIndexOf('('));
        }

        // Retrieve the lyrics
        // If we're retrying, the lyrics have already been retrieved
        if (!retried) {
            getLyrics(Artist, Title, Album);
        }

        // If there's no preferred language, just keep the language that the song is in
        // If the lyric API call didn't return a language tag, just default it to English
        if (preferredLanguage.equals("")) {
            if (!lyricsLanguageTag.equals("")) {
                preferredLanguage = lyricsLanguageTag;
            } else {
                preferredLanguage = "en";
            }
        }


        // If we have previously generated emoji lyrics for a song/language combo, we just use the saved version
        SpannableStringBuilder storedLyrics = checkIfAlreadyGeneratedEmojiLyrics(lyricsLanguageTag + preferredLanguage, Title, Artist, context);
        if (!storedLyrics.equals(SpannableStringBuilder.valueOf(""))) {
            // All setTexts need to be run on the UI thread to avoid android.view.ViewRootImpl$CalledFromWrongThreadException (only the original thread that created a view hierarchy can touch its views)
            new Handler(Looper.getMainLooper()).post(() -> {
                lyric_container.setText(storedLyrics);
                Toast.makeText(context, "Retrieved previously generated lyrics", Toast.LENGTH_LONG).show(); // fixes java.lang.RuntimeException: Can't toast on a thread that has not called Looper.prepare()
            });
            return;
        }


        // SHORT_WORDS contains all the 2-letter words that will match emojis
        // all others would return a flag emoji (song lyrics would most likely use the full name of the country,
        // not the abbreviation, so these would be errors) or cause problems with the checks
        final HashSet<String> SHORT_WORDS = new HashSet<>(Arrays.asList("cd", "id", "ok", "on", "ox", "tv", "up", "vs"));

        // All of these are valid English words (possible lyrics) that will return an emoji that does not fit the meaning
        // Generated by applying the reverse version of the checks on each tag and alias
        // (ex. alias "struggling" -> struggling -> struggl, struggle, strugg, struggler),
        // checked for validity using Python's nltk.corpus package,
        // and going through results manually to determine if emoji is correct
        final HashSet<String> WILL_RETURN_INCORRECT_EMOJI = new HashSet<>(Arrays.asList("aer", "aes", "ager", "agin'", "aging",
                "ain't", "ary", "ass", "askin'", "asking", "awin'", "awing", "bac", "badge", "badgerer", "ban", "barb", "barbe", "bas", "bater", "batin'",
                "bating", "batter", "bay", "bearder", "beardin'", "bearding", "bearer", "bearin'", "bearing", "bein'", "being", "beginning", "bel", "belly",
                "beni", "bent", "bentin'", "benting", "ber", "bes", "bice", "bier", "biting", "bitin'", "bin'", "bing", "bis", "blindlin'", "blindling",
                "blo", "bloc", "boa", "board", "bom", "bon", "bonair", "boo", "boomer", "booty", "bowl", "boyer", "brin'", "bring", "broo",
                "bur", "burg", "buss", "busy", "cal", "came", "cand", "carer", "cater", "card", "cha", "char", "chil", "chin", "chin'",
                "ching", "cho", "clappin'", "clapping", "class", "clin'", "cling", "clow", "club", "coater", "coatin'",
                "coating", "col", "colin'", "coling", "come", "comin'", "coming", "consol", "consolin'", "consoling", "coo", "cooin'", "cooing",
                "coper", "copin'", "coping", "copy", "cor", "corin'", "coring", "corner", "cos", "coucher", "couchin'", "couching",
                "couplin'", "coupling", "cower", "craber", "cran", "crea", "creamer", "creamy", "cricketer", "cricketin'", "cricketing",
                "crow", "crowin'", "crowing", "crus", "crusher", "crushin'", "crushing", "cub", "curr", "custom", "dang", "dar",
                "darin'", "daring", "darter", "das", "dee", "deserter", "dicin'", "dicing", "din'", "ding", "director", "doer", "doin'",
                "doing", "dos", "drago", "dram", "dresser", "dressin'", "dressing", "duckin'", "ducking", "earin'", "earing",
                "eer", "eggin'", "egging", "eighty", "engage", "envelop", "errin'", "erring", "ers", "ess", "factor", "fair", "fairin'",
                "fairing", "fastin'", "fasting", "fee", "fence", "filin'", "filing", "filmy", "fir", "flighter", "flightin'", "flighting", "fryin'", "fryin'",
                "flow", "flush", "fogy", "foo", "fou", "fourer", "franc", "fro", "galler", "gam", "gas", "ger", "gif", "gin'", "ging", "givin'", "giving",
                "goa", "goin'", "going", "gol", "grapin'", "graping", "gree", "grind", "grouper", "halter", "han", "hander", "happin'", "happing", "hasher",
                "hater", "helin'", "heling", "hin'", "hing", "hippin'", "hipping", "hop", "huer", "icin'", "icing", "I'd", "ide", "I'll",
                "innin'", "inning", "iter", "its", "it's", "jeer", "jin'", "jing", "kier", "kin", "land", "las", "lawin'", "lawing", "lay",
                "lea", "leafer", "leave", "ledge", "ledgin'", "ledging", "leger", "lier", "lin", "lin'", "ling", "linin'",
                "lining", "lis", "living", "loo", "lys", "makin'", "making", "mal", "malt", "maltin'", "malting", "mang", "many", "mas", "may", "mil", "min'", "ming", "mone",
                "moo", "mooin'", "mooing", "mooner", "moonin'", "mooning", "neer", "need", "newin'", "newing", "nig", "nigh", "not",
                "numb", "numbin'", "numbing", "ode", "oer", "offer", "offin'", "offing", "omer", "oner", "ons", "owin'", "owing",
                "owler", "owlin'", "owling", "pad", "page", "pain", "painin'", "paining", "pant", "pantin'", "panting", "park",
                "parkin", "part", "past", "pastin'", "pasting", "pea", "peacher", "peer", "per", "pes", "peter", "phon", "phot",
                "pian", "picker", "pilin'", "piling", "pin'", "ping", "plan", "pooler", "pounder", "poundin'", "pounding",
                "pow", "presenter", "pressin'", "pressing", "pridin'", "priding", "privateer", "pursed", "rabbi", "rag", "rainer", "rame",
                "rater", "ratin'", "rating", "red", "register", "rewin", "ringin'", "ringing", "roc", "roer", "ruer", "ruin'", "ruing",
                "sal", "sant", "scar", "scoot", "scout", "scoutin'", "scouting", "scree", "screener", "screenin'", "screening",
                "secre", "seed", "seein'", "seeing", "seer", "senega", "ser", "sevener", "shee", "sher", "shi", "shiel", "shielin'",
                "shieling", "shin", "sho", "shoo", "show", "showin'", "showing", "shy", "sic", "sier", "sill", "sin", "sis", "sker", "skid",
                "slin'", "sling", "sober", "soss", "spade", "spaer", "sparkless", "speeder", "speedin'", "speeding", "spor",
                "springer", "springin'", "springing", "starer", "starin'", "staring", "startin'", "starting", "stin'", "sting",
                "stoper", "stopin'", "stoping", "string", "strings", "sty", "sunn", "swa", "sweater", "swing", "tad", "takin'", "taking", "targe", "tax", "taxin'", "taxing", "tearer",
                "tearin'", "tearing", "ten", "theat", "thin", "thin'", "thing", "things", "tig", "tige", "tin'", "ting", "tire", "tog",
                "tong", "too", "toot", "tra", "track", "trainer", "trainin'", "training", "trainy", "try", "tryin'", "trying", "used", "ust", "vas", "veer", "vier", "vis",
                "wale", "wat", "wear", "wearin'", "wearing", "weatherin'", "weathering", "wer", "whin", "win'", "winder",
                "windin'", "winding", "wing", "wint", "wiper", "wips", "wis", "woo", "wooin'", "wooing", "yar", "yer", "zer",
                "zin'", "zing", "zombi", "zoo"));

// If song does not return lyrics for some reason, no need to go through the whole checking process
        if (lyrics.equals("")) {
            // All setTexts need to be run on the UI thread to avoid android.view.ViewRootImpl$CalledFromWrongThreadException (only the original thread that created a view hierarchy can touch its views)
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    lyric_container.setText("Lyrics unavailable for this song");
                }
            });
        }

        else {

            // This will get set to true if any lyric is converted to an emoji
            // If it's false at the end, there was most likely an issue with translation/language tags
            boolean canGenerateEmojis = false;

            // So we can go through word by word
            String[] lyricList = lyrics.split("\\*");
            // Build a new string so we can include the emojis
            SpannableStringBuilder emojiLyrics = new SpannableStringBuilder(); // Final string

            // Get ready for translation
            /* Has to be declared for translations to work, and it's better to declare it once here and have it
             *  not be needed than to do it for every line when it is needed (putting it in an if statement here doesn't work) */
            IamAuthenticator authenticator;
            LanguageTranslator languageTranslator = null;

            if (lyrics == "") {
                authenticator = new IamAuthenticator("");
                languageTranslator = new LanguageTranslator("2018-05-01", authenticator);
                languageTranslator.setServiceUrl("https://api.eu-gb.language-translator.watson.cloud.ibm.com/instances/f7e68812-ed0c-449f-8af5-3beb318abde6");
            }

// 1. Read each line. Translate if need be then split on spaces so we can read each word

            // If we need to translate to English or between non-English languages, do it line by line
            for (String line : lyricList) {
                StringBuilder lineLyrics = new StringBuilder();

                if (!lyricsLanguageTag.equals(preferredLanguage) && line.trim().equals("")) { // Something in the split made lines with just spaces part of the list (only a problem when translating)
                    continue;
                }

                // Line break tags will be removed by the translator
                if (line.contains("\n") || line.contains("\r") || line.contains("\r\n") || line.contains("\r\n\r\n")) {
                    emojiLyrics.append(line); // If we need to translate, line breaks trigger the "Translation unavailable" response
                    continue;
                }

                // Emoji checking only works for English words
                // If it's a non-English song with no translation selected, we'll do the translations to get emojis during inner loop (since we don't need to display original lyrics separately)
                if (!lyricsLanguageTag.equals("en") && !lyricsLanguageTag.equals(preferredLanguage)) {
                    emojiLyrics.append(returnPunctuation(line)).append("\n"); // We'll display the original lyrics then the translated ones on the next line

                    // 'til and 'Til are not translated properly
                    line = line.replace("'til","until").replace("'Til","Until");

                    if (!lyricsLanguageTag.equals("")) {
                        try {
                            TranslateOptions translateOptions = new TranslateOptions.Builder()
                                    .addText(line)
                                    .modelId(lyricsLanguageTag + "-en")
                                    .build();

                            line = languageTranslator.translate(translateOptions)
                                    .execute().getResult().getTranslations().get(0).getTranslation();
                        } catch (Exception e){
                            try {
                                TranslateOptions translateOptions = new TranslateOptions.Builder()
                                        .addText(line)
                                        .target("en")
                                        .build();

                                line = languageTranslator.translate(translateOptions)
                                        .execute().getResult().getTranslations().get(0).getTranslation();
                            } catch (Exception e2) {
                                // Translator can't determine source language

                                emojiLyrics.append("(Translation unavailable (translator cannot determine source language)) \n\n");
                                continue;
                            }
                        }
                    }
                }


                String[] lineList = line.split(" ");

// 2. Read each word. The words will need to be in English if they aren't already.
//    If it's a non-English song and we are not translating it, we need to translate it word-by-word to English

                for (String lyric : lineList) {

                    String originalLyric = "";

                    // If a non-English song isn't being translated to another language, translate to English word by word
                    // For each word, add the original word to lineLyrics and just use English translation to match emojis,
                    // adding the emoji after the original word
                    if (!lyricsLanguageTag.equals("en") && lyricsLanguageTag.equals(preferredLanguage)) {
                        originalLyric = lyric;

                        // 'til and 'Til do not get translated properly
                        if (lyric.equals("'til")) {
                            lyric = "until";
                        }
                        else if (lyric.equals("'Til")) {
                            lyric = "Until";
                        }

                        // Don't bother trying to translate if the lyric is a punctuation character
                        if (!lyric.matches("[?!(),._]")) {
                            try {
                                TranslateOptions translateOptions = new TranslateOptions.Builder()
                                        .addText(lyric)
                                        .modelId(lyricsLanguageTag + "-en")
                                        .build();

                                lyric = languageTranslator.translate(translateOptions)
                                        .execute().getResult().getTranslations().get(0).getTranslation();
                            } catch (Exception e) {
                                try {
                                    TranslateOptions translateOptions = new TranslateOptions.Builder()
                                            .addText(lyric)
                                            .target("en")
                                            .build();

                                    lyric = languageTranslator.translate(translateOptions)
                                            .execute().getResult().getTranslations().get(0).getTranslation();
                                } catch (Exception e2) {
                                    // Translator can't determine source language
                                    Log.v("lyrics", "Translator cannot determine source language for this lyric: " + lyric);
                                }
                            }
                        }

                    }
                    if (!originalLyric.equals("")) {
                        lineLyrics.append(originalLyric).append(" ");
                    } else {
                        lineLyrics.append(lyric).append(" ");
                    }

// 3. Convert lyrics to emojis

                    /* Sample emoji JSON object */
                    /* We will be using the aliases (unique to each emoji) and tags (may be shared) */
//                    {
////                        "emojiChar": "😄",
////                        "emoji": "\uD83D\uDE04",
////                        "description": "smiling face with open mouth and smiling eyes",
////                        "aliases": [
////                        "smile"
////                        ],
////                        "tags": [
////                        "happy",
////                        "joy",
////                        "pleased"
////                        ]
////                  }


                    // All the emoji tags and aliases are in lower case
                    // Replace all non-word characters (punctuation) besides hyphens
                    // Replace hyphens with underscores (emoji tags and aliases use underscores)
                    lyric = lyric.toLowerCase();

                    // We don't need single letter emojis (or number emojis if the lyric is in number form anyway
                    if (lyric.length() <= 2 && !SHORT_WORDS.contains(lyric)) {
                        continue;
                    }

                    if (WILL_RETURN_INCORRECT_EMOJI.contains(lyric)
                            || (lyric.length() == 4 && lyric.charAt(2) == '\'' && !lyric.equals("he's"))) { // These are for 2 letter words that
                        continue;                                                                           // have an 's at the end ("he's" is fine though)
                    }


// 3a. These are some possible exceptions to the checks (detailed later; they would return an incorrect emoji or none at all)
// It's easier just to check for them individually than try to make a rule
                    // -hard to define
                    // -only one occurrence (faster for everything else to make this one check than call getForAlias and getForTag an extra time for a new rule)
                    // -would interfere with other emojis
                    // -alias has underscores and no tags (would never match a lyric)
                    // - etc.

                    // Returns person emoji
                    // Would not return anything but the emoji fits the word
                    if (lyric.equals("face")) {
                        lineLyrics.append("(\uD83E\uDDD1) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns man emoji
                    // Would not return anything but the emoji fits the words
                    if (lyric.equals("he") || lyric.equals("him") || lyric.equals("his") || lyric.equals("he's") || lyric.equals("he'll") || lyric.equals("sir")) {
                        lineLyrics.append("(\uD83D\uDC68) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns woman emoji
                    // Would not return anything but the emoji fits the words
                    if (lyric.equals("she") || lyric.equals("her") || lyric.equals("hers") || lyric.equals("she's") || lyric.equals("she'll")) {
                        lineLyrics.append("(\uD83D\uDC69) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    if (lyric.equals("asleep") || lyric.equals("sleeps")) {
                        lineLyrics.append("(\uD83D\uDE34)");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns American flag emoji
                    //) "us" is the alias, but it's an actual word and these words are more likely to appear in a song anyway
                    if (lyric.equals("usa") || lyric.equals("u.s.a.") || lyric.equals("american") || lyric.equals("america")) {
                        lineLyrics.append("(\uD83C\uDDFA\uD83C\uDDF8) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns ocean emoji
                    // Would return waving hand in plural check
                    if (lyric.equals("waves")) {
                        lineLyrics.append("(\uD83C\uDF0A) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns lying face emoji
                    // The alias contains underscores and there are no tags
                    if (lyric.equals("lying") || lyric.equals("lie") || lyric.equals("lyin'") || lyric.equals("lies")
                            || lyric.equals("liar")) {
                        lineLyrics.append("(\uD83E\uDD25) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns punch emoji
                    // Would not return anything but this emoji fits the word
                    if (lyric.equals("fight") || lyric.equals("fights")) {
                        lineLyrics.append("(\uD83D\uDC4A) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns foot emoji
                    // Would not return anything but this emoji fits the word
                    if (lyric.equals("kick") || lyric.equals("kicking")) {
                        lineLyrics.append("(\uD83E\uDDB6) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns smiling devil emoji
                    // Would not return anything but this emoji fits the word
                    if (lyric.equals("bad")) {
                        lineLyrics.append("(\uD83D\uDE08) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns brain emoji
                    // Would not return anything but this emoji fits the word
                    if (lyric.equals("mind")) {
                        lineLyrics.append("(\uD83E\uDDE0) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns boy emoji
                    // There is no alias or tag for) "kid"
                    if (lyric.equals("kid") || lyric.equals("kids")) {
                        lineLyrics.append("(\uD83D\uDC3A) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns dancer emoji
                    // A rule for lyric + last letter + -er words (win -> winner) would have too many exceptions
                    if (lyric.equals("dance") || lyric.equals("dances") || lyric.equals("danced")) {
                        lineLyrics.append("(\uD83D\uDC83) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns trophy emoji
                    // A rule for lyric + last letter + -er words (win -> winner) would have too many exceptions
                    if (lyric.equals("win")) {
                        lineLyrics.append("(\uD83C\uDFC6) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns runner emoji
                    // A rule for lyric + last letter + -er words (run -> runner) would have too many exceptions
                    // Past tense is irregular
                    if (lyric.equals("run") || lyric.equals("ran")) {
                        lineLyrics.append("(\uD83C\uDFC3) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns fishing pole emoji
                    // The alias contains underscores and there are no tags
                    if (lyric.equals("fishing")) {
                        lineLyrics.append("(\uD83c\uDFA3) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns tree emoji
                    // There are no emojis with just "tree" as a tag or alias
                    if (lyric.equals("tree") || lyric.equals("trees")) {
                        lineLyrics.append("(\uD83C\uDF33) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns smiling face with halo emoji
                    // Would not return anything but this fits the word
                    if (lyric.equals("halo") || lyric.equals("halos")) {
                        lineLyrics.append("(\uD83D\uDE07) ");
                        canGenerateEmojis = true;
                        continue;
                    }


                    // Returns swimmer emoji
                    // A rule for lyric + last letter + -er words (swim -> swimmer) would have too many exceptions
                    // Past tense is irregular
                    if (lyric.equals("swim") || lyric.equals("swam")) {
                        lineLyrics.append("(\uD83C\uDFCA) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns cityscape emoji
                    // There is no alias or tag for "city"
                    if (lyric.equals("city") || lyric.equals("cities")) {
                        lineLyrics.append("(\uD83C\uDFD9) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns game die emoji
                    // Would return poop emoji but this fits the word
                    if (lyric.equals("craps")) {
                        lineLyrics.append("(\uD83C\uDFB2) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns newspaper emoji
                    // Would not return anything but this fits the word
                    if (lyric.equals("news")) {
                        lineLyrics.append("(\uD83D\uDCF0) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns music notes emoji
                    // Would not return anything but this fits the word
                    if (lyric.equals("rhythm")) {
                        lineLyrics.append("(\uD83C\uDFB6) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns mouth emoji
                    // Would not return anything because "mouth" is not an alias or tag
                    if (lyric.equals("mouth")) {
                        lineLyrics.append("(\uD83D\uDC44) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns page facing up emoji
                    // Would not return anything but this fits the word
                    if (lyric.equals("page")) {
                        lineLyrics.append("(\uD83D\uDCC4) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns microphone emoji
                    // Would not return anything but this fits the word
                    if (lyric.equals("singers")) {
                        lineLyrics.append("(\uD83C\uDFA4) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns speaking head in silhouette emoji
                    // Would not return anything but this fits the words
                    if (lyric.equals("speak") || lyric.equals("speaks") || lyric.equals("speaking") || lyric.equals("speakin'")
                            || lyric.equals("say") || lyric.equals("saying") || lyric.equals("sayin'")) {
                        lineLyrics.append("(\uD83D\uDDE3) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns spy emoji
                    // Would not return anything but this fits the words
                    if (lyric.equals("sneak") || lyric.equals("sneaks") || lyric.equals("sneaky") || lyric.equals("sneaking") ||
                            lyric.equals("sneakin'")) {
                        lineLyrics.append("(\uD83D\uDD75) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns cyclone emoji
                    // Would not return anything but this fits the word
                    if (lyric.equals("hurricane")) {
                        lineLyrics.append("(\uD83C\uDF00) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns thunder cloud and rain emoji
                    // Would not return anything but this fits the word
                    if (lyric.equals("storm") || lyric.equals("storms") || lyric.equals("stormy") || lyric.equals("thunderstruck")) {
                        lineLyrics.append("(\u26C8) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns speaking head in silhouette emoji
                    // Would not return anything but this fits the words
                    if (lyric.equals("street") || lyric.equals("streets")) {
                        lineLyrics.append("(\uD83D\uDEE3) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns honey pot emoji
                    // The alias contains underscores and there are no tags
                    if (lyric.equals("honey")) {
                        lineLyrics.append("(\uD83C\uDF6F) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns lightning bolt emoji
                    // Would not return anything but this fits the word
                    if (lyric.equals("electric") || lyric.equals("electricity")) {
                        lineLyrics.append("(\u26A1) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns sleepy face emoji
                    // Would not return anything but this fits the word
                    if (lyric.equals("sleep") || lyric.equals("sleeps")) {
                        lineLyrics.append("(\uD83D\uDE34) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns walking emoji
                    // Would not return anything but this fits the word
                    if (lyric.equals("walks")) {
                        lineLyrics.append("(\uD83D\uDEB6) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns panda face emoji
                    // The alias contains underscores and there are no tags
                    if (lyric.equals("panda")) {
                        lineLyrics.append("(\uD83D\uDC3C) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns bust in silhouette emoji
                    // Would not return anything but this fits the word
                    if (lyric.equals("shadow") || lyric.equals("shadows") || lyric.equals("shadow's")) {
                        lineLyrics.append("(\uD83D\uDC64) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns wolf face emoji
                    // The plural follows the -f -> -ves rule and it is the only emoji like this
                    //      -> This is faster than adding a rule to the plural checks
                    if (lyric.equals("wolves")) {
                        lineLyrics.append("(\uD83D\uDC3A) ");
                        canGenerateEmojis = true;
                        continue;
                    }

                    // Returns castle emoji
                    // The alias contains underscores and there are no tags
                    if (lyric.equals("castle")) {
                        lineLyrics.append("(\uD83C\uDFF0) ");
                        canGenerateEmojis = true;
                        continue;
                    }

// End of exceptions

// 3b. Try matching for the alias

                    // Unicode will display as emoji in the app
                    // "(emoji) "
                    Emoji emoji = EmojiManager.getForAlias(lyric);
                    if (emoji != null) { // Match found
                        lineLyrics.append("(").append(emoji.getUnicode()).append(") ");
                        canGenerateEmojis = true;
                        continue;
                    }

// 3c. Try matching the tags

                    // If no alias match, try to match the tags
                    // This returns a Set<Emoji> (because there might be more than one match for tags)
                    Set<Emoji> emojis = EmojiManager.getForTag(lyric); // method from emoji library
                    if (emojis != null) { // Matches found
                        // Pick a random number index in range(emojis.size())
                        // Iterate index - 1 times
                        // The final iteration gives the chosen random emoji, so add it to lineLyrics
                        Random rand = new Random();
                        int index = rand.nextInt(emojis.size());
                        Iterator<Emoji> iter = emojis.iterator();
                        for (int i = 0; i < index; i++) {
                            iter.next();
                        }
                        // "(emoji) "
                        Emoji randomChoice = iter.next();
                        lineLyrics.append("(").append((randomChoice.getUnicode())).append(") ");
                        canGenerateEmojis = true;

                        continue;
                    }

// 4. ***CHECKS***
// Check if word is different form of an alias/tag (plural, -ing, etc.)


// 4a. Add all possible forms of the lyric to ArrayList
                    ArrayList<String> checks = new ArrayList<>();

                    // No need to check length since "ing" wouldn't be a lyric
                    // Logic for first 3 checks taken from: https://github.com/notwaldorf/emoji-translate/blob/master/emoji-translate.js
                    if (lyric.contains("ing") || lyric.contains("in'")) {
                        String verb = lyric.substring(0, lyric.length() - 3);

                        // eating -> eat
                        checks.add(verb);

                        // dancing -> dance
                        checks.add(verb + 'e');

                        // stopping -> stop
                        checks.add(verb.substring(0, verb.length() - 1));

                        // -ing to -er form (swimming -> swimmer, surfing -> surfer)
                        checks.add(verb + "er");
                    }

                    // adjective -> noun (cloudy -> cloud)
                    // -y -> -ies (fry -> fries)
                    // There are exceptions to this rule but none that would return an incorrect emoji
                    else if (lyric.substring(lyric.length() - 1).equals("y")) {
                        String minusY = lyric.substring(0, lyric.length() - 1);
                        checks.add(minusY);
                        checks.add(minusY + "ies");
                    }

                    // -ies -> -y (parties -> party)
                    else if (lyric.length() > 3 && lyric.substring(lyric.length() - 3).equals("ies")) {
                        checks.add(lyric.substring(0, lyric.length() - 3) + 'y');
                    } else {

                        // Don't do if - else-if for these 2 since there is overlap between the groups that can't easily be accounted for
                        // ex. "loved" --> "lov", "love"

                        // Unpluralize (sings -> sing)
                        // past -> present (smiled -> smile)
                        // Get rid of colon (sing: -> sing)
                        String lastOne = lyric.substring(lyric.length() - 1);
                        if (lastOne.equals("s") || lastOne.equals("d")) {
                            checks.add(lyric.substring(0, lyric.length() - 1));
                        }

                        // Undo possessive (man's -> man)
                        // past -> infinitive (blushed -> blush)
                        String lastTwo = lyric.substring(lyric.length() - 2);
                        if (lastTwo.equals("'s") || lastTwo.equals("ed")) {
                            checks.add(lyric.substring(0, lyric.length() - 2));
                        }

                        // Plural
                        // Logic taken from: https://github.com/notwaldorf/emoji-translate/blob/master/emoji-translate.js
                        checks.add(lyric + 's');

                        // Verb to -ing form (walk -> walking)
                        checks.add(lyric + "ing");
                        // Verb to -er form (climb -> climber)
                        checks.add(lyric + "er");
                        // infinitive to past tense/adjective (amaze -> amazed)
                        checks.add(lyric + "d");
                        // infinitive to past tense/adjective (disappoint -> disappointed)
                        checks.add(lyric + "ed");
                        // sleep -> sleepy
                        checks.add(lyric + "y");
                    }

// 4b. Now go through the check variations and try to find a match
                    for (String possibleMatch : checks) {
                        // Try matching for the alias
                        // Unicode will display as emoji in the app
                        // "(emoji) "
                        emoji = EmojiManager.getForAlias(possibleMatch);
                        if (emoji != null) { // Match found
                            lineLyrics.append("(").append(emoji.getUnicode()).append(") ");
                            canGenerateEmojis = true;
                            break;
                        }

                        // If no alias match, try to match the tags
                        // This returns a Set<Emoji> (because there might be more than one match for tags)
                        emojis = EmojiManager.getForTag(possibleMatch);
                        if (emojis != null) { // Matches found
                            // Pick a random number index in range(emojis.size())
                            // Iterate index - 1 times
                            // The final iteration gives the chosen random emoji, so add it to lineLyrics
                            Random rand = new Random();
                            int index = rand.nextInt(emojis.size());
                            for (int i = 0; i < index; i++) {
                                emojis.iterator().next();
                            }
                            // "(emoji) "
                            lineLyrics.append("(").append((emojis.iterator().next().getUnicode())).append(") ");
                            canGenerateEmojis = true;
                        }
                    }
                }

// 5. Add the processed line to emojiLyrics (translate if need be)

                // If translating from English, add the processed original line here
                String punctuationReturned = returnPunctuation(lineLyrics.toString());
                if (lyricsLanguageTag.equals("en") && !preferredLanguage.equals("en")) {
                    emojiLyrics.append(punctuationReturned).append("\n");
                }
                // lineLyrics are already in English, so we only need to re-translate if preferred is another language
                if (!lyricsLanguageTag.equals(preferredLanguage) && (!preferredLanguage.equals("en"))) {

                    try {
                        TranslateOptions translateOptions = new TranslateOptions.Builder()
                                .addText(punctuationReturned)
                                .modelId("en-" + preferredLanguage)
                                .build();
                        try {
                            String result = languageTranslator.translate(translateOptions)
                                    .execute().getResult().getTranslations().get(0).getTranslation();

                            // Make translated text red
                            SpannableString spanResult = new SpannableString(result);
                            spanResult.setSpan(new ForegroundColorSpan(Color.RED), 0, result.length(), 0);

                            emojiLyrics.append(spanResult).append("\n\n");

                        }
                        catch (Exception e) {
                        }
                    }
                    catch (Exception e) { // Just in case lineLyrics was not in English/there was a problem, shouldn't happen though
                        try {
                            TranslateOptions translateOptions = new TranslateOptions.Builder()
                                    .addText(punctuationReturned)
                                    .target(preferredLanguage)
                                    .build();

                            try {
                                String result = languageTranslator.translate(translateOptions)
                                        .execute().getResult().getTranslations().get(0).getTranslation();

                                // Make translated text red
                                SpannableString spanResult = new SpannableString(result);
                                spanResult.setSpan(new ForegroundColorSpan(Color.RED), 0, result.length(), 0);

                                emojiLyrics.append(spanResult).append("\n\n");

                            } catch (Exception e2) {
                            }
                        } catch (Exception e2) {
                            // Translator can't determine source language
                            e2.printStackTrace();
                        }
                    }
                    continue;
                }
                if (lyricsLanguageTag.equals(preferredLanguage)) { // The line will already contain emojis
                    emojiLyrics.append(punctuationReturned);
                } else {
                    // Make translated text red
                    SpannableString spanResult = new SpannableString(punctuationReturned);
                    spanResult.setSpan(new ForegroundColorSpan(Color.RED), 0, lineLyrics.toString().length(), 0);

                    emojiLyrics.append(spanResult).append("\n\n");
                }

            }

// 6. Add emojified/translated lyrics to lyric_container
            if (!canGenerateEmojis) {
                // If no emojis can be generated for the song for whatever reason, let the user know and then just display the lyrics
                if (retried) {
                    String warning = "**There was a problem determining the language of this song**\n**Emojis/translations could not be generated**\n\n";
                    // All setTexts need to be run on the UI thread to avoid android.view.ViewRootImpl$CalledFromWrongThreadException (only the original thread that created a view hierarchy can touch its views)
                    new Handler(Looper.getMainLooper()).post(() ->
                            lyric_container.setText(warning + returnPunctuation(lyrics)));
                }
                // If no emojis are generated during lyricProcessing(), we'll retry once as if the song language and language preference are in English
                // If it fails again, we'll just return the original lyrics
                else {
                    retried = true;
                    preferredLanguage = "en";
                    lyricsLanguageTag = "en";
                    lyricProcessing(Album, Title, Artist, preferredLanguage, context, lyric_container);
                }
            }
            else {
                // Get rid of extra spaces before punctuation
                // use replace with regex? replace(" [A-Z]", "[A-Z]")?

                // All setTexts need to be run on the UI thread to avoid android.view.ViewRootImpl$CalledFromWrongThreadException (only the original thread that created a view hierarchy can touch its views)
                new Handler(Looper.getMainLooper()).post(() ->
                        lyric_container.setText(emojiLyrics, TextView.BufferType.SPANNABLE));

                saveEmojiLyrics(emojiLyrics, lyricsLanguageTag + preferredLanguage, Title, Artist, context);
            }
        }
    }

    private String returnPunctuation(String lyrics) {
        return lyrics.replace(" *\n* ", "\n").replace(" *\r* ", "\r").replace(" ,", ",").replace(" ?", "?").replace(" .", ".").replace("_", "-").replace(" !", "!").replace("( ", "(").replace(" )", ")"); //.replace(" '", "'")
    }

    private String spacePunctuation(String lyrics) {
        return lyrics.replace("\n", " *\n* ").replace("\r", " *\r* ").replace(",", " ,").replace("?", " ?").replace(".", " .").replace("-", "_").replace("!", " !").replace("(", "( ").replace(")", " )"); //.replace("'", " '")
    }

    private void saveEmojiLyrics(SpannableStringBuilder lyrics, String languages, String Title, String Artist, Context context){
        SharedPreferences simpleAppInfo = context.getSharedPreferences("SongInfo", Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = simpleAppInfo.edit();
        String songInfo = Title + Artist + languages;
        editor.putString(songInfo, HtmlCompat.toHtml(lyrics,HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)); // Html.toHtml is because we can't use SpannableString with shared preferences
        editor.apply();
    }

    private SpannableStringBuilder checkIfAlreadyGeneratedEmojiLyrics(String languages, String Title, String Artist, Context context){
        SharedPreferences generatedLyrics = context.getSharedPreferences("SongInfo", Context.MODE_PRIVATE);
        String songInfo = Title + Artist + languages;
        if (generatedLyrics.contains(songInfo)) {
            return (SpannableStringBuilder)HtmlCompat.fromHtml(generatedLyrics.getString(songInfo, ""), HtmlCompat.FROM_HTML_MODE_LEGACY); // Html.fromHtml converts back to SpannableString
        }
        return SpannableStringBuilder.valueOf("");
    }
}
