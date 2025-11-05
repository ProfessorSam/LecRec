package de.professorsam.lecrec;

import com.github.sardine.Sardine;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class IntegrationTest {

    static Network testNet = Network.newNetwork();

    @Container
    static WireMockContainer mockApi = new WireMockContainer("wiremock/wiremock:3.6.0")
            .withNetwork(testNet)
            .withNetworkAliases("uni.local")
            .withEnv("WIREMOCK_OPTIONS", "--verbose")
            .withMappingFromJSON("""
                    {
                      "mappings": [
                        {
                          "request": {
                            "method": "GET",
                            "urlPath": "/api/livestream/events",
                            "queryParameters": {
                              "limit": {
                                "equalTo": "1"
                              },
                              "series": {
                                "equalTo": "testseriesid"
                              }
                            }
                          },
                          "response": {
                            "status": 200,
                            "headers": {
                              "Content-Type": "application/json"
                            },
                            "jsonBody": {
                              "count": 1,
                              "next": null,
                              "previous": null,
                              "results": [
                                {
                                  "id": "e1e1e1e1-e1e1-e1e1-e1e1-e1e1e1e1e1e1",
                                  "title": "Mocked Test Series Event",
                                  "agent_id": "M-TEST",
                                  "series": {
                                    "id": "testseriesid",
                                    "title": "Mocked Test Series"
                                  },
                                  "start": \"""" + Instant.now().toString() + "\"," + """
                                  "end": "2025-10-27T16:00:00+01:00",
                                  "active": true,
                                  "protected": true
                                }
                              ]
                            }
                          }
                        },
                        {
                          "request": {
                            "method": "GET",
                            "urlPath": "/api/livestream/events/e1e1e1e1-e1e1-e1e1-e1e1-e1e1e1e1e1e1",
                            "queryParameters": {
                              "password": {
                                "equalTo": "stream"
                              }
                            }
                          },
                          "response": {
                            "status": 200,
                            "headers": {
                              "Content-Type": "application/json"
                            },
                            "jsonBody": {
                              "id": "e1e1e1e1-e1e1-e1e1-e1e1-e1e1e1e1e1e1",
                              "title": "Mocked Test Series Event",
                              "active": true,
                              "protected": true,
                              "manifest": "https://test-streams.mux.dev/x36xhzz/url_6/193039199_mp4_h264_aac_hq_7.m3u8"
                            }
                          }
                        }
                      ]
                    }
                    """)
            .withExposedPorts(8080);

    @Container
    static GenericContainer<?> webdav = new GenericContainer<>("bytemark/webdav:latest")
            .withNetwork(testNet)
            .withNetworkAliases("webdav.local")
            .withEnv("USERNAME", "test")
            .withEnv("PASSWORD", "test")
            .withEnv("WEBDAV_PATH", "var/lib/dav")
            .withExposedPorts(80);

    @Container
    static GenericContainer<?> app = new GenericContainer<>("ghcr.io/professorsam/lecrec:1.2")
            .withNetwork(testNet)
            .withEnv("LECREC_USERNAME", "test")
            .withEnv("LECREC_PASSWORD", "test")
            .withEnv("LECREC_DIRECTORY", Base64.getEncoder().encodeToString("".getBytes()))
            .withEnv("LECREC_ENDPOINT", Base64.getEncoder().encodeToString("http://webdav.local".getBytes()))
            .withEnv("LECREC_URLS", Base64.getEncoder().encodeToString("http://uni.local/livestream/viewer/series/testseriesid?password=stream".getBytes()))
            .withEnv("LECREC_API_BASE", "http://uni.local:8080")
            .withNetworkAliases("lecrec.local")
            .withExposedPorts(8000)
            .dependsOn(mockApi, webdav);

    @BeforeAll
    static void setup() {
        System.out.println("Starting Containers");
        mockApi.start();
        webdav.start();
    }

    @Test
    void integrationTest() throws IOException {
        app.start();
        app.followOutput(s -> System.out.print(s.getUtf8String()));
        System.out.println("Started test");
        waitForStreamToFinish();
        System.out.println("Finished test. Checking results");
        Sardine sardine = com.github.sardine.SardineFactory.begin("test", "test");
        String endpoint = "http://" + webdav.getHost() + ":" + webdav.getMappedPort(80) + "/";
        assertTrue(sardine.exists(endpoint));
        assertTrue(!sardine.list(endpoint).isEmpty());
    }

    private void waitForStreamToFinish(){
        while(true){
            try {
                Thread.sleep(1000);
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().get().url("http://" + app.getHost() + ":" + app.getMappedPort(8000) + "/api/recorders").build();
                try (Response response = client.newCall(request).execute()){
                    JSONArray json = new JSONArray(response.body().string());
                    if(json.isEmpty()){
                        continue;
                    }
                    JSONObject obj = json.getJSONObject(0);
                    if(obj.getString("streamState").equals(StreamState.UPLOADING_STREAM.name())){
                        Thread.sleep(3000);
                        break;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
