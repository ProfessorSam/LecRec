package de.professorsam.lecrec;

import com.github.sardine.Sardine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class IntegrationTest {

    static Network testNet = Network.newNetwork();

    @Container
    static WireMockContainer mockApi = new WireMockContainer("wiremock/wiremock:3.6.0")
            .withNetwork(testNet)
            .withNetworkAliases("uni.local")
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
            .withEnv("LECREC_DIRECTORY", Base64.getEncoder().encodeToString("upload".getBytes()))
            .withEnv("LECREC_ENDPOINT", Base64.getEncoder().encodeToString("http://webdav.local".getBytes()))
            .withEnv("LECREC_URLS", Base64.getEncoder().encodeToString("http://uni.local/livestream/viewer/series/testseriesid?password=stream".getBytes()))
            .withEnv("LECREC_API_BASE", "http://uni.local:8080")
            .dependsOn(mockApi, webdav);

    @BeforeAll
    static void setup() {
        System.out.println("Starting Containers");
        mockApi.start();
        webdav.start();
    }

    @Test
    void integrationTest() throws InterruptedException, IOException {
        app.start();
        System.out.println("Started test");
        Thread.sleep(Duration.of(30, ChronoUnit.SECONDS));
        System.out.println("Finished test. Checking results");
        Sardine sardine = com.github.sardine.SardineFactory.begin("test", "test");
        String endpoint = "http://" + webdav.getHost() + ":" + webdav.getMappedPort(80) + "/upload/";
        assertTrue(sardine.exists(endpoint));
        assertTrue(!sardine.list(endpoint).isEmpty());
    }

}
