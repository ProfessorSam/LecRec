package de.professorsam.lecrec;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;

public class Recorder extends Thread {
    private static final OkHttpClient httpclient = new OkHttpClient();
    private static final String apiBase = System.getenv().getOrDefault(
            "LECREC_API_BASE",
            "https://dash.uni.electures.uni-muenster.de"
    );

    private final String streamurl;
    private final String seriesID;
    private final String password;

    private String eventID;
    private JSONObject nextEventJson;
    private JSONObject currentStreamJson;
    private Instant nextStreamStart;
    private StreamState streamState = StreamState.SEARCH_NEXT_EVENT;

    public Recorder(String streamurl) {
        super();
        this.streamurl = streamurl;
        this.seriesID = extractSeriesId();
        this.password = extractPassword();
        System.out.println("Password: " + seriesID + " " + password);
    }

    @Override
    public void run() {
        System.out.println("Starting Recorder main loop...");
        while (true) {
            try {
                switch (streamState) {
                    case SEARCH_NEXT_EVENT -> searchNextEvent();
                    case WAITING_FOR_STREAM -> waitForStream();
                    case RECORDING_STREAM -> recordStream();
                    case UPLOADING_STREAM -> uploadStream();
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Unexpected error. Retrying in 15 minutes...");
                sleepMinutes(15);
                streamState = StreamState.SEARCH_NEXT_EVENT;
            }
        }
    }

    private void searchNextEvent() {
        eventID = getNextEventId(seriesID);
        if (eventID == null) {
            System.out.println("No upcoming event found. Retrying in 15 minutes...");
            sleepMinutes(15);
            return;
        }

        try {
            JSONArray array = nextEventJson.getJSONArray("results");
            JSONObject nextEvent = array.getJSONObject(0);
            String start = nextEvent.getString("start");
            OffsetDateTime dateTime = OffsetDateTime.parse(start);

            System.out.println("Next event starts at " + dateTime);

            nextStreamStart = dateTime.toInstant().plusSeconds(30);
            streamState = StreamState.WAITING_FOR_STREAM;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error parsing next event time. Retrying...");
            sleepMinutes(15);
        }
    }

    private void waitForStream() {
        if (nextStreamStart == null) {
            streamState = StreamState.SEARCH_NEXT_EVENT;
            return;
        }

        long millisUntilStart = Duration.between(Instant.now(), nextStreamStart).toMillis();
        if (millisUntilStart > 0) {
            System.out.println("Waiting " + millisUntilStart / 1000 + " seconds for next stream...");
            sleepMillis(millisUntilStart);
        }

        String streamUrl = getStreamUrl(eventID, password);
        if (streamUrl == null) {
            System.out.println("Stream not active or URL missing. Retrying in 15 minutes...");
            sleepMinutes(15);
            streamState = StreamState.SEARCH_NEXT_EVENT;
            return;
        }

        this.currentStreamJson = new JSONObject();
        this.currentStreamJson.put("url", streamUrl);
        streamState = StreamState.RECORDING_STREAM;
    }

    private void recordStream() {
        String streamUrl = currentStreamJson.optString("url", null);
        if (streamUrl == null) {
            System.out.println("Stream URL not available, searching again...");
            streamState = StreamState.SEARCH_NEXT_EVENT;
            return;
        }

        System.out.println("Recording stream: " + streamUrl);
        File outdir = new File("/streams");
        outdir.mkdirs();
        String filename = Instant.now().getEpochSecond() + ".mp4";
        File file = new File(outdir, filename);

        try {
            String[] command = {
                    "ffmpeg",
                    "-i", streamUrl,
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-movflags", "+faststart",
                    "-max_reload", "0",
                    "-timeout", "5000000",
                    "-rw_timeout", "5000000",
                    "-loglevel", "warning",
                    outdir.getPath() + "/" + filename
            };
            ;
            Process p = new ProcessBuilder().inheritIO().command(command).start();
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Recording failed. Retrying search...");
            streamState = StreamState.SEARCH_NEXT_EVENT;
            return;
        }

        if (file.exists()) {
            currentStreamJson.put("recordedFile", file.getAbsolutePath());
            streamState = StreamState.UPLOADING_STREAM;
        } else {
            System.out.println("Recorded file missing. Retrying search...");
            streamState = StreamState.SEARCH_NEXT_EVENT;
        }
    }

    private void uploadStream() {
        String username = System.getenv("LECREC_USERNAME");
        String password = System.getenv("LECREC_PASSWORD");
        String directory = System.getenv("LECREC_DIRECTORY");
        String endpoint = System.getenv("LECREC_ENDPOINT");

        if (username == null || password == null || directory == null || endpoint == null) {
            System.out.println("Upload skipped: Missing environment variables.");
            streamState = StreamState.SEARCH_NEXT_EVENT;
            return;
        }

        directory = new String(Base64.getDecoder().decode(directory), StandardCharsets.UTF_8);
        endpoint = new String(Base64.getDecoder().decode(endpoint), StandardCharsets.UTF_8);

        if (!endpoint.endsWith("/")) endpoint += "/";
        if (directory.startsWith("/")) directory = directory.substring(1);
        if (!directory.endsWith("/")) directory += "/";

        String filePath = currentStreamJson.optString("recordedFile", null);
        if (filePath == null) {
            System.out.println("No recorded file found to upload.");
            streamState = StreamState.SEARCH_NEXT_EVENT;
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File not found: " + file.getAbsolutePath());
            streamState = StreamState.SEARCH_NEXT_EVENT;
            return;
        }

        String credentials = username + ":" + password;
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        String targetUrl = endpoint + directory + file.getName();

        System.out.println("Uploading to: " + targetUrl);

        RequestBody body = RequestBody.create(file, MediaType.parse("application/octet-stream"));
        Request request = new Request.Builder()
                .url(targetUrl)
                .header("Authorization", basicAuth)
                .put(body)
                .build();

        try (Response response = httpclient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.out.println("Upload failed: " + response.code() + " " + response.message());
                sleepMinutes(15);
            } else {
                System.out.println("Upload successful!");
                Files.delete(Paths.get(file.getAbsolutePath()));
            }
        } catch (Exception e) {
            System.out.println("Upload exception: " + e.getMessage());
            e.printStackTrace();
            sleepMinutes(15);
        }

        streamState = StreamState.SEARCH_NEXT_EVENT;
    }

    // --- Helper methods ---

    private String getStreamUrl(String eventID, String password) {
        String apiUrl = apiBase + "/api/livestream/events/" + eventID;
        if (password != null) apiUrl += "?password=" + password;

        Request request = new Request.Builder().url(apiUrl).build();
        try (Response response = httpclient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.out.println("Could not get stream URL.");
                return null;
            }

            JSONObject json = new JSONObject(response.body().string());
            if (!json.getBoolean("active")) {
                System.out.println("Stream not active yet.");
                return null;
            }
            return json.getString("manifest");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getNextEventId(String seriesID) {
        String apiUrl = apiBase + "/api/livestream/events?limit=1&series=" + seriesID;
        Request request = new Request.Builder().url(apiUrl).build();

        try (Response response = httpclient.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            JSONObject jsonObject = new JSONObject(response.body().string());
            nextEventJson = jsonObject;
            JSONArray results = jsonObject.getJSONArray("results");
            JSONObject nextEvent = results.getJSONObject(0);
            return nextEvent.getString("id");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String extractPassword() {
        try {
            String[] split = streamurl.split("/");
            String last = split[split.length - 1];
            String password = last.split("\\?")[1];
            return password.substring(9);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSeriesId() {
        String[] split = streamurl.split("/");
        String last = split[split.length - 1];
        return last.split("\\?")[0];
    }

    private void sleepMinutes(int min) {
        try {
            Thread.sleep(Duration.ofMinutes(min).toMillis());
        } catch (InterruptedException ignored) {
        }
    }

    private void sleepMillis(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
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
