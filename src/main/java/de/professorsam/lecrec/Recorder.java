package de.professorsam.lecrec;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public class Recorder extends Thread{
    private static final OkHttpClient httpclient = new OkHttpClient();
    private final String streamurl;

    private final String seriesID;

    private final String password;
    private String eventID;
    private JSONObject nextEventJson;
    private JSONObject currentStreamJson;
    private Instant nextStreamStart;
    private StreamState streamState = StreamState.SEARCH_NEXT_EVENT;
    public Recorder(String streamurl){
        super();
        this.streamurl = streamurl;
        seriesID = extractSeriesId();
        password = extractPassword();
        System.out.println("Password: " + seriesID + " " + password);
    }

    @Override
    public void run() {
        eventID = getNextEventId(seriesID);
        if(eventID == null){
            System.out.println("Could not find next event. Waiting 15 minutes untill retrying");
            retrySearchingForNextEvent();
            return;
        }
        scheduleThreadSleepToEventStart();
    }

    private void scheduleThreadSleepToEventStart(){
        streamState = StreamState.WAITING_FOR_STREAM;
        try {
            JSONArray array = nextEventJson.getJSONArray("results");
            JSONObject nextEvent = array.getJSONObject(0);
            String start = nextEvent.getString("start");
            System.out.println("Next event starts at " + start +". Sleeping...");
            OffsetDateTime dateTime = OffsetDateTime.parse(start);
            if(dateTime.isBefore(OffsetDateTime.now())){
                System.out.println("Event starts in the past. Try downloading");
                downloadStream();
                return;
            }
            System.out.println("Event will start at " + dateTime);
            Instant targetTime = dateTime.toInstant().plusSeconds(30);
            nextStreamStart = targetTime;
            long millis = Duration.between(Instant.now(), targetTime).toMillis();
            Thread.sleep(millis);
            downloadStream();
        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not get start time of next event. Retrying in 15 minutes");
            retrySearchingForNextEvent();
        }
    }

    private void downloadStream() {
        String streamUrl = getStreamUrl(eventID, password);
        if(streamUrl == null){
            System.out.println("Could not get stream url. Retrying in 15 minutes");
            retrySearchingForNextEvent(false);
            return;
        }
        System.out.println("Stream url: " + streamUrl);
        streamState = StreamState.RECORDING_STREAM;
        String filename = Instant.now().getEpochSecond() + ".mkv";
        try {
            Runtime.getRuntime().exec(new String[]{"ffmpeg" , "-i",  streamUrl, "-c",  "copy", filename}).waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        File file = new File(filename);
        File outdir = new File("/streams");
        outdir.mkdirs();
        streamState = StreamState.UPLOADING_STREAM;
        try {
            Files.copy(file.toPath(), Paths.get("/streams"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        streamState = StreamState.SEARCH_NEXT_EVENT;
        retrySearchingForNextEvent();
    }

    private String getStreamUrl(String eventID, String password){
        String apiUrl = "https://dash.uni.electures.uni-muenster.de/api/livestream/events/" + eventID;
        if(password != null){
            apiUrl += "?password=" + password;
        }
        System.out.println(apiUrl);
        Request request = new Request.Builder()
                .url(apiUrl)
                .build();
        try (Response response = httpclient.newCall(request).execute()) {
            if(!response.isSuccessful()){
                System.out.println("Could not get stream url");
                return null;
            }
            String json = response.body().string();
            JSONObject jsonObject = new JSONObject(json);
            currentStreamJson = jsonObject;
            System.out.println(json);
            if(!jsonObject.getBoolean("active")){
                System.out.println("Stream not active");
                return null;
            }
            return jsonObject.getString("manifest");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void retrySearchingForNextEvent(){
        retrySearchingForNextEvent(true);
    }

    private void retrySearchingForNextEvent(boolean sleepFor15Minutes){
        try {
            while (streamState == StreamState.SEARCH_NEXT_EVENT){
                if(sleepFor15Minutes){
                    Thread.sleep(Duration.of(15, ChronoUnit.MINUTES));
                }
                eventID = getNextEventId(seriesID);
                if(eventID != null){
                    streamState = StreamState.WAITING_FOR_STREAM;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String getNextEventId(String seriesID){
        String apiUrl = "https://dash.uni.electures.uni-muenster.de/api/livestream/events?limit=1&series=" + seriesID;
        Request request = new Request.Builder()
                .url(apiUrl)
                .build();
        try (Response response = httpclient.newCall(request).execute()){
            if(!response.isSuccessful()){
                return null;
            }
            String json = response.body().string();
            JSONObject jsonObject = new JSONObject(json);
            nextEventJson = jsonObject;
            JSONArray results = jsonObject.getJSONArray("results");
            JSONObject nextEvent = results.getJSONObject(0);
            return nextEvent.getString("id");
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private String extractPassword() {
        try {
            String[] split = streamurl.split("/");
            String last = split[split.length - 1];
            String password = last.split("\\?")[1];
            password = password.substring(9);
            System.out.println("Password: " + password);
            return password;
        } catch (Exception e){
            return null;
        }
    }

    private String extractSeriesId(){
        String[] split = streamurl.split("/");
        String last = split[split.length - 1];
        String id = last.split("\\?")[0];
        return id;
    }

    public Instant getNextStreamStart() {
        return nextStreamStart;
    }

    public String getSeriesID() {
        return seriesID;
    }

    public StreamState getStreamState() {
        return streamState;
    }
}
