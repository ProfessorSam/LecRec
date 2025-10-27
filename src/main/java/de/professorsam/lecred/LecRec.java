package de.professorsam.lecred;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class LecRec {
    private static final OkHttpClient httpclient = new OkHttpClient();

    public static void main(String[] args) {
        var lecrec = new LecRec();
        String streamurl = "https://dash.uni.electures.uni-muenster.de/livestream/viewer/series/0cba2815-575b-4135-ae7f-308b140ba578?password=Wgy88dcr4cfMxDeV";
        String seriesID = lecrec.extractSeriesId(streamurl);
        String password = lecrec.extractPassword(streamurl);
        System.out.println(seriesID + " - " + password);
        String eventID = lecrec.getNextEventId(seriesID);
        System.out.println("Eventid: " + eventID);
        if(eventID == null){
            return;
        }
        String streamUrl = lecrec.getStreamUrl(eventID, password);
        System.out.println(streamUrl);
    }

    private void downloadStream(String streamurl){
        try {
            Runtime.getRuntime().exec(new String[]{"ffmpeg" , "-i",  streamurl, "-c",  "copy", "live-mkv.mkv"});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String extractPassword(String streamurl) {
        try {
            String[] split = streamurl.split("/");
            String last = split[split.length - 1];
            String password = last.split("\\?")[1];
            password = password.substring(9);
            return password;
        } catch (Exception e){
            return null;
        }
    }

    private String extractSeriesId(String url){
        String[] split = url.split("/");
        String last = split[split.length - 1];
        String id = last.split("\\?")[0];
        return id;
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
            System.out.println(json);
            JSONObject jsonObject = new JSONObject(json);
            JSONArray results = jsonObject.getJSONArray("results");
            JSONObject nextEvent = results.getJSONObject(0);
            return nextEvent.getString("id");
        } catch (IOException e){
            e.printStackTrace();
            return null;
        }
    }

    private String getStreamUrl(String eventID, String password){
        String apiUrl = "https://dash.uni.electures.uni-muenster.de/api/livestream/events/" + eventID;
        if(password != null){
            apiUrl += "?password=" + password;
        }
        Request request = new Request.Builder()
                .url(apiUrl)
                .build();
        try (Response response = httpclient.newCall(request).execute()) {
            if(!response.isSuccessful()){
                return null;
            }
            String json = response.body().string();
            JSONObject jsonObject = new JSONObject(json);
            if(!jsonObject.getBoolean("active")){
                System.out.println("Stream not active");
                return null;
            }
            return jsonObject.getString("manifest");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
