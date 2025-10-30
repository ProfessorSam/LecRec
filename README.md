# LecRec — Automated Livestream Recorder and Uploader

LecRec is a small, container-friendly Java 21 application that automatically:

- Watches one or more livestream “series” pages
- Detects the next scheduled event time via an API
- Starts recording the HLS/DASH stream when the event begins (using ffmpeg)
- Uploads the finished recording to a WebDAV endpoint
- Repeats continuously for upcoming events

It also exposes a minimal web UI and JSON API to observe recorder status.


## Key Features

- Multiple sources: Provide one or many viewer URLs via a single env var
- Auto-scheduling: Computes sleep time until the next event start
- Robust start: Handles past-start events by attempting immediate download
- Recording via ffmpeg: HLS to MKV with audio transcoded to AAC
- WebDAV upload: Creates target directories if missing and uploads recordings
- Lightweight status server (Javalin):
  - HTML status page at `/`
  - JSON status at `/api/recorders`
- Docker-ready: Includes Dockerfile and `docker-compose.yml`
- Integration test via Testcontainers to validate end‑to‑end behavior


## How it Works

1. On startup, the app reads `LECREC_URLS` (Base64-encoded comma-separated list of viewer URLs).
2. For each URL, a `Recorder` thread is created.
3. Each recorder extracts the `seriesID` and optional `password` from the URL.
4. It uses the livestream API to discover the next event of the series.
5. It sleeps until about 30 seconds after the scheduled start and begins recording with `ffmpeg`.
6. When recording finishes (default max 3 hours as safety), the file is uploaded to the configured WebDAV endpoint.
7. The recorder returns to searching for the next event and repeats.

The app also starts a tiny HTTP server on port 8000 to display status and expose metrics for automation.


## Status UI and JSON API

- UI: `GET /` — a simple dashboard showing each recorder, state and next start.
- JSON: `GET /api/recorders` — returns an array of recorder objects, for example:
  ```json
  [
    {
      "seriesID": "abc123",
      "streamState": "WAITING_FOR_STREAM",
      "nextStreamStart": "2025-10-27T15:59:30Z",
      "streamurl": "https://dash.uni.electures.uni-muenster.de/livestream/embed_viewer/series/abc123"
    }
  ]
  ```

Stream states (`StreamState` enum):
- `SEARCH_NEXT_EVENT` — Looking up the next series event
- `WAITING_FOR_STREAM` — Sleeping until the next start time
- `RETRYING_LOADING_STREAM` — Backoff before retrying to obtain the stream URL
- `RECORDING_STREAM` — Recording in progress (ffmpeg)
- `UPLOADING_STREAM` — Uploading the file to WebDAV


## Configuration (Environment Variables)

Most variables are expected to be Base64-encoded to avoid quoting/space issues in Docker.

- `LECREC_URLS` (required): Base64-encoded string of one or more viewer URLs separated by commas.
  - Example (decoded):
    ```
    http://uni.local/livestream/viewer/series/<series-id>?password=<pwd>,http://…
    ```
- `LECREC_USERNAME` (required): WebDAV username
- `LECREC_PASSWORD` (required): WebDAV password
- `LECREC_ENDPOINT` (required): Base64-encoded WebDAV base URL
  - Example (decoded): `https://webdav.example.com`
- `LECREC_DIRECTORY` (required): Base64-encoded path inside the WebDAV endpoint where files should be uploaded
  - Example (decoded): `Uploads/Lectures/WS25`
- `LECREC_API_BASE` (optional): Base URL for the livestream JSON API used by discovery and for building links in the UI
  - Defaults to `https://dash.uni.electures.uni-muenster.de`. Should alos work for TUM, but not verified

Notes:
- Ensure Base64 encoding has no newlines and is plain (URL-safe not required unless your tooling enforces it).
- Multiple URLs in `LECREC_URLS` must be comma-separated before Base64 encoding.


## Recording Details

- Output directory (inside container): `/streams`
- Filename: UNIX epoch seconds + `.mp4`
- ffmpeg arguments (excerpt):
  ```
  -reconnect 1 -reconnect_streamed 1 -reconnect_delay_max 10 \
  -fflags +genpts+igndts+discardcorrupt \
  -i <HLS_URL> -c:v copy -c:a aac -b:a 128k -movflags +faststart -t 03:00:00
  ```
- After upload, the local file is deleted.


## Build and Run (Local)

Prerequisites:
- Java 21
- Gradle
- Docker (optional for container build/test)

Build the fat jar:
```
./gradlew shadowJar
```

Run locally (example using PowerShell; ensure env vars are set appropriately):
```
java -jar build\libs\LecRec-1.0-SNAPSHOT-all.jar
```


## Docker

Build the image via Gradle task (uses the Dockerfile):
```
./gradlew buildDockerImage
```
This tags the image as `ghcr.io/professorsam/lecrec:1.2`.

Run with Docker:
```
docker run --rm -p 8000:8000 \
  -v "C:\\recordings:/streams" \
  -e LECREC_URLS=<BASE64_URLS> \
  -e LECREC_USERNAME=<user> \
  -e LECREC_PASSWORD=<pass> \
  -e LECREC_ENDPOINT=<BASE64_ENDPOINT> \
  -e LECREC_DIRECTORY=<BASE64_DIR> \
  ghcr.io/professorsam/lecrec:1.2
```


## docker-compose

A sample compose file is provided in `docker-compose.yml`. Consider updating the image tag to `1.2` to match the build:
```yaml
services:
  lecrec:
    image: ghcr.io/professorsam/lecrec:1.2
    ports:
      - "8000:8000"
    volumes:
      - "/mnt/hetznerbox/streams:/streams"
    environment:
      - LECREC_URLS=${LECREC_URLS}
      - LECREC_USERNAME=${LECREC_USERNAME}
      - LECREC_PASSWORD=${LECREC_PASSWORD}
      - LECREC_ENDPOINT=${LECREC_ENDPOINT} # Base64-encoded
      - LECREC_DIRECTORY=${LECREC_DIRECTORY} # Base64-encoded
```

You can place a `.env` file alongside your compose file, e.g.:
```
LECREC_URLS=BASE64(...)
LECREC_USERNAME=...
LECREC_PASSWORD=...
LECREC_ENDPOINT=BASE64(https://webdav.example.com)
LECREC_DIRECTORY=BASE64(Uploads/Semester1/Lectures)
```


## Integration Test

The project includes an integration test using Testcontainers that:
- Spins up WireMock to mimic the livestream API
- Spins up a WebDAV server container
- Starts the LecRec container with env vars
- Waits briefly and asserts that a file was uploaded to WebDAV

Run tests:
```
./gradlew test
```
Note: The test depends on building the Docker image (handled automatically via Gradle task dependency). 
Downloading the test stream requires an internet connection


## Troubleshooting

- No uploads appear:
  - Verify WebDAV credentials and URL
  - Ensure `LECREC_ENDPOINT` and `LECREC_DIRECTORY` are valid Base64 and decode to the expected values
  - Check container logs for "Uploading Stream to:" lines
- Recorder never starts recording:
  - Confirm the livestream API is reachable (check `LECREC_API_BASE`)
  - Ensure the viewer URL contains a valid series ID and password if required
- Permission issues on host path:
  - Confirm the `/streams` host mount is writable by the container user
- Base64 pitfalls:
  - Make sure there are no trailing newlines and that you encoded the raw string, not quotes


## Development Notes

- Main entry: `de.professorsam.lecrec.LecRec`
- Core classes: `LecRec`, `Recorder`, `StreamState`
- Minimal HTTP server: Javalin on port `8000`
- Dependencies: OkHttp, org.json, Sardine (WebDAV), Javalin, JUnit, Testcontainers, WireMock


## License

This project is licensed under the MIT license.
