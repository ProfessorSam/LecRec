package de.professorsam.lecrec;

import io.javalin.Javalin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class LecRec {

    private static final List<Recorder> recorders = new ArrayList<>();

    public static void main(String[] args) {
        byte[] decoded = Base64.getDecoder().decode(System.getenv("LECREC_URLS"));
        String urls = new String(decoded, StandardCharsets.UTF_8);
        String[] urlArray = urls.split(",");
        for(String url : urlArray){
            Recorder recorder = new Recorder(url);
            recorders.add(recorder);
            recorder.start();
        }
        var app = Javalin.create()
                .get("/", ctx -> ctx.html(html))
                .get("/api/recorders", ctx -> {
                    try {
                        JSONArray root = new JSONArray();
                        recorders.forEach(recorder -> {
                            JSONObject rec = new JSONObject();
                            rec.put("seriesID", recorder.getSeriesID());
                            rec.put("streamState", recorder.getStreamState());
                            rec.put("nextStreamStart", recorder.getNextStreamStart() != null ? recorder.getNextStreamStart().toString() : Instant.now().minusSeconds(5).toString());
                            rec.put("streamurl", System.getenv("LECREC_API_BASE") == null ? "https://dash.uni.electures.uni-muenster.de" : System.getenv("LECREC_API_BASE") + "/livestream/embed_viewer/series/" + recorder.getSeriesID());
                            root.put(rec);
                        });
                        ctx.json(root.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .start(8000);
    }


    private static final String html = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1" />
              <title>Stream Recorders — Status</title>
            
              <style>
              :root {
                --bg: #0f1724;
                --card: #0b1220;
                --muted: #98a0b3;
                --accent: #7c5cff;
                --success: #34d399;
                --warn: #f59e0b;
                --danger: #ff6b6b;
                --glass: rgba(255, 255, 255, 0.04);
                font-family: Inter, ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial;
              }
            
              html, body {
                height: 100%;
              }
            
              body {
                margin: 0;
                background: linear-gradient(180deg, #071027 0%, var(--bg) 100%);
                color: #e6eef8;
                -webkit-font-smoothing: antialiased;
                -moz-osx-font-smoothing: grayscale;
                padding: 28px;
              }
            
              header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                gap: 16px;
                margin-bottom: 18px;
                flex-wrap: wrap;
              }
            
              .title {
                display: flex;
                gap: 12px;
                align-items: center;
                flex-wrap: wrap;
              }
            
              .logo {
                width: 52px;
                height: 52px;
                border-radius: 10px;
                display: grid;
                place-items: center;
                background: linear-gradient(135deg, var(--accent), #4cc9f0);
                box-shadow: 0 6px 20px rgba(124, 92, 255, 0.12), inset 0 -6px 18px rgba(255, 255, 255, 0.02);
                font-weight: 700;
                color: white;
                font-size: 18px;
                flex-shrink: 0;
              }
            
              h1 {
                font-size: 20px;
                margin: 0;
              }
            
              p.lead {
                margin: 0;
                color: var(--muted);
                font-size: 14px;
              }
            
              .controls {
                display: flex;
                gap: 10px;
                align-items: center;
                flex-shrink: 0;
              }
            
              button {
                background: transparent;
                color: var(--muted);
                border: 1px solid rgba(255, 255, 255, 0.04);
                padding: 8px 12px;
                border-radius: 8px;
                cursor: pointer;
                font-size: 14px;
              }
            
              button:hover {
                color: white;
                border-color: rgba(255, 255, 255, 0.08);
              }
            
              main {
                max-width: 1100px;
                margin: auto;
              }
            
              .grid {
                display: grid;
                grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
                gap: 16px;
                margin-top: 18px;
              }
            
              .card {
                background: linear-gradient(180deg, rgba(255, 255, 255, 0.02), rgba(255, 255, 255, 0.01));
                border-radius: 12px;
                padding: 16px;
                box-shadow: 0 6px 18px rgba(2, 6, 23, 0.6);
                border: 1px solid rgba(255, 255, 255, 0.02);
                display: flex;
                gap: 12px;
                align-items: flex-start;
                overflow: hidden;
                min-width: 0; /* prevents text overflow */
              }
            
              .left {
                width: 56px;
                height: 56px;
                border-radius: 10px;
                background: var(--glass);
                display: flex;
                align-items: center;
                justify-content: center;
                flex-shrink: 0;
                font-weight: 600;
                font-size: 12px;
                color: var(--muted);
              }
            
              .body {
                flex: 1;
                min-width: 0; /* important for text ellipsis */
              }
            
              .meta {
                display: flex;
                align-items: center;
                gap: 8px;
                margin-bottom: 6px;
              }
            
              .meta > div {
                min-width: 0; /* prevents flex overflow */
              }
            
              .series {
                font-weight: 700;
              }
            
              .small {
                font-size: 13px;
                color: var(--muted);
              }
            
              .url {
                max-width: 100%;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                word-break: break-all;
              }
            
              .state-pill {
                padding: 6px 9px;
                border-radius: 999px;
                font-weight: 700;
                font-size: 12px;
                display: inline-flex;
                align-items: center;
                gap: 8px;
                white-space: nowrap;
              }
            
              .state-search { background: linear-gradient(90deg, #374151, #1f2937); color: #e6eef8; }
              .state-wait { background: linear-gradient(90deg, #0ea5a4, #06b6d4); color: #06202b; }
              .state-record { background: linear-gradient(90deg, #fb7185, #ef4444); color: white; }
              .state-upload { background: linear-gradient(90deg, #f59e0b, #f97316); color: white; }
            
              .countdown {
                font-weight: 700;
                font-size: 13px;
                margin-top: 10px;
                white-space: nowrap;
              }
            
              .muted {
                color: var(--muted);
                font-size: 13px;
              }
            
              .progress-wrap {
                width: 100%;
                height: 8px;
                background: rgba(255, 255, 255, 0.03);
                border-radius: 999px;
                overflow: hidden;
                margin-top: 12px;
              }
            
              .progress {
                height: 100%;
                width: 0%;
                background: linear-gradient(90deg, var(--accent), #4cc9f0);
                transition: width 400ms ease;
              }
            
              .small-quiet {
                font-size: 12px;
                color: var(--muted);
                margin-top: 8px;
              }
            
              /* responsive adjustments */
              @media (max-width: 480px) {
                header {
                  flex-direction: column;
                  align-items: flex-start;
                  gap: 10px;
                }
                .grid {
                  grid-template-columns: 1fr;
                }
              }
            
              .dot {
                width: 10px;
                height: 10px;
                border-radius: 999px;
                display: inline-block;
              }
            </style>
            
            </head>
            <body>
              <header>
                <div class="title">
                  <div class="logo">REC</div>
                  <div>
                    <h1>Stream Recorders — Status</h1>
                    <p class="lead">Live overview of all recorders and their states.</p>
                  </div>
                </div>
            
                <div class="controls">
                  <button id="refreshBtn" title="Refresh now">Refresh</button>
                  <button id="toggleAuto" title="Toggle auto reload">Auto: <span id="autoState">ON</span></button>
                </div>
              </header>
            
              <main>
                <section id="summary" class="small-quiet">Loading recorders…</section>
            
                <div class="grid" id="grid"></div>
              </main>
            
              <!-- Recorder template (modular) -->
              <template id="recorder-template">
                <article class="card">
                  <div class="left" aria-hidden="true">
                    <!-- initial icon/short id -->
                    <span class="short-id" style="font-size:13px"></span>
                  </div>
            
                  <div class="body">
                    <div class="meta">
                      <div>
                        <div class="series">Series <span class="series-id"></span></div>
                        <div class="small muted url" style="max-width:420px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"></div>
                      </div>
            
                      <div style="margin-left:auto;text-align:right">
                        <div class="state-pill">
                          <span class="dot state-dot"></span>
                          <span class="state-text">STATE</span>
                        </div>
                        <div class="small muted" style="margin-top:6px">Updated: <span class="updated">—</span></div>
                      </div>
                    </div>
            
                    <div class="small muted description">Next stream start: <span class="nextStream">—</span></div>
                    <div class="countdown" aria-live="polite"><span class="countdownText">—</span></div>
            
                    <div class="progress-wrap" aria-hidden="true">
                      <div class="progress"></div>
                    </div>
            
                    <div class="small-quiet">State explanation: <span class="state-explain">—</span></div>
                  </div>
                </article>
              </template>
            
              <script>
                // Mapping of StreamState to UI classes, explanations and progress behavior
                const STATE_MAP = {
                  "SEARCH_NEXT_EVENT": {
                    pillClass: "state-search",
                    dotColor: "#94a3b8",
                    text: "Searching",
                    explain: "Looking for the next event in the series.",
                    progress: 0
                  },
                  "WAITING_FOR_STREAM": {
                    pillClass: "state-wait",
                    dotColor: "#06b6d4",
                    text: "Waiting",
                    explain: "Scheduled — waiting for the event start. Countdown shown when available.",
                    progress: 30
                  },
                  "RECORDING_STREAM": {
                    pillClass: "state-record",
                    dotColor: "#ff6b6b",
                    text: "Recording",
                    explain: "Recording live stream to disk.",
                    progress: 100
                  },
                  "UPLOADING_STREAM": {
                    pillClass: "state-upload",
                    dotColor: "#f59e0b",
                    text: "Uploading",
                    explain: "Uploading finished recording to /streams or remote storage.",
                    progress: 80
                  }
                };
            
            
                let sampleRecorders = [
                  {
                    seriesID: "abc123",
                    streamState: "WAITING_FOR_STREAM",
                    nextStreamStart: "2025-10-28T19:30:00Z",
                    streamurl: "https://dash.uni.../abc123?password=xyz"
                  },
                  {
                    seriesID: "def456",
                    streamState: "RECORDING_STREAM",
                    nextStreamStart: null,
                    streamurl: "rtmp://live.example/def456"
                  },
                  {
                    seriesID: "ghi789",
                    streamState: "SEARCH_NEXT_EVENT",
                    nextStreamStart: null,
                    streamurl: "https://dash.uni.../ghi789"
                  }
                ];
            
                const grid = document.getElementById('grid');
                const tmpl = document.getElementById('recorder-template');
                const refreshBtn = document.getElementById('refreshBtn');
                const toggleAuto = document.getElementById('toggleAuto');
                const autoState = document.getElementById('autoState');
                const summary = document.getElementById('summary');
            
                let auto = true;
                let lastData = [];
                let countdownIntervals = [];
            
                async function fetchRecorders(){
            
                  try {
                    const resp = await fetch('/api/recorders', {cache: "no-store"});
                    if(!resp.ok) throw new Error('no /api/recorders');
                    const data = await resp.json();
                    return data;
                  } catch (e) {
                    return sampleRecorders;
                  }
                }
            
                function clearCountdowns(){
                  countdownIntervals.forEach(i => clearInterval(i));
                  countdownIntervals = [];
                }
            
                function render(recorders){
                  lastData = recorders;
                  grid.innerHTML = '';
                  clearCountdowns();
            
                  if(!Array.isArray(recorders) || recorders.length === 0){
                    summary.textContent = "No recorders found.";
                    return;
                  } else {
                    const counts = recorders.reduce((acc,r)=>{
                      acc[r.streamState] = (acc[r.streamState]||0) + 1;
                      return acc;
                    }, {});
                    summary.textContent = `${recorders.length} recorder(s) — ` +
                      Object.entries(counts).map(([k,v]) => `${v} ${k}`).join(' · ');
                  }
            
                  recorders.forEach((rec, idx) => {
                    const node = tmpl.content.cloneNode(true);
                    const card = node.querySelector('.card');
                    const left = node.querySelector('.left');
                    const shortId = node.querySelector('.short-id');
                    const series = node.querySelector('.series-id');
                    const url = node.querySelector('.url');
                    const statePill = node.querySelector('.state-pill');
                    const stateText = node.querySelector('.state-text');
                    const stateDot = node.querySelector('.state-dot');
                    const updated = node.querySelector('.updated');
                    const nextStream = node.querySelector('.nextStream');
                    const countdownText = node.querySelector('.countdownText');
                    const progress = node.querySelector('.progress');
                    const stateExplain = node.querySelector('.state-explain');
            
            
                    shortId.textContent = (rec.seriesID || '—').slice(0,6).toUpperCase();
                    series.textContent = rec.seriesID || '—';
                    url.textContent = rec.streamurl || '';
                    updated.textContent = new Date().toLocaleString();
            
                    const s = STATE_MAP[rec.streamState] || {
                      pillClass: "state-search",
                      dotColor: "#9ca3af",
                      text: rec.streamState || "UNKNOWN",
                      explain: "No explanation available.",
                      progress: 0
                    };
            
                    statePill.classList.add(s.pillClass);
                    stateText.textContent = s.text;
                    stateDot.style.background = s.dotColor;
                    stateExplain.textContent = s.explain;
            
            
                    progress.style.width = s.progress + '%';
            
            
                    if(rec.nextStreamStart){
                      try {
                        const target = new Date(rec.nextStreamStart);
                        nextStream.textContent = target.toLocaleString();
                        function tick(){
                          const now = new Date();
                          const diff = target - now;
                          if(diff <= 0){
                            countdownText.textContent = "Starting soon";
                            progress.style.width = "60%";
                            return;
                          }
                          const hours = Math.floor(diff/1000/3600);
                          const minutes = Math.floor((diff/1000/60) % 60);
                          const seconds = Math.floor((diff/1000) % 60);
                          countdownText.textContent = `${hours}h ${minutes}m ${seconds}s`;
                          const totalWindow = Math.max(60, Math.min(7*24*3600, (target - now)/1000 + 1));
                          const pct = Math.max(6, Math.min(98, Math.round(100 * (1 - diff / (totalWindow*1000)))));
                          progress.style.width = pct + '%';
                        }
                        tick();
                        const id = setInterval(tick, 1000);
                        countdownIntervals.push(id);
                      } catch(e){
                        nextStream.textContent = rec.nextStreamStart;
                        countdownText.textContent = "—";
                      }
                    } else {
                      nextStream.textContent = "—";
                      countdownText.textContent = rec.streamState === 'RECORDING_STREAM' ? "Live now" : "—";
                    }
            
                    grid.appendChild(node);
                  });
                }
            
                async function refresh(){
                  try {
                    const recs = await fetchRecorders();
                    render(recs);
                  } catch (e){
                    summary.textContent = 'Could not load recorders: ' + e.message;
                  }
                }
            
                refreshBtn.addEventListener('click', () => refresh());
                toggleAuto.addEventListener('click', () => {
                  auto = !auto;
                  autoState.textContent = auto ? 'ON' : 'OFF';
                });
            
                // auto refresh loop
                (function autoLoop(){
                  if(auto) refresh();
                  setTimeout(autoLoop, 5000);
                })();
            
                // initial
                refresh();
              </script>
            </body>
            </html>
            """;
}
