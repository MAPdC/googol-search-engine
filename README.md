# 🌐 Googol — Distributed Search Engine

A fully distributed web search engine inspired by Google, Qwant, and Ecosia. Built as the final project for the **Distributed Systems** course at the University of Coimbra, Googol features an automated web crawler, a fault-tolerant replicated index, real-time statistics via WebSocket, and AI-powered search analysis.

---

## 🏗️ Architecture

```
                     ┌───────────────────────────────────────┐
                     │          World Wide Web               │
                     └──────────────────┬────────────────────┘
                                        │ HTTP
                     ┌──────────────────▼────────────────────┐
                     │       Downloaders  (N parallel)       │
                     │  jsoup crawler · Reliable multicast   │
                     └─────────┬──────────────────┬──────────┘
                               │ RMI multicast    │ RMI addUrl
             ┌─────────────────▼──────────┐  ┌────▼─────────────────┐
             │  Index Storage Barrel 1    │  │     URL Queue        │
             │  InvertedIndex + snapshots │  │ BloomFilter · CQueue │
             └─────────────────┬──────────┘  └──────────────────────┘
             ┌─────────────────▼──────────┐
             │  Index Storage Barrel 2    │  ← identical replica
             └─────────────────┬──────────┘
                               │ RMI (search / backlinks / stats)
                    ┌──────────▼────────────────────────────┐
                    │           RMI Gateway                 │
                    │ Round-robin LB · Fault-tolerant retry │
                    └──────┬──────────────────────┬─────────┘
             RMI (console) │                      │ RMI (web server)
        ┌──────────────────▼─────┐  ┌─────────────▼──────────────────────┐
        │   RMI Console Client   │  │  Spring Boot Web Server            │
        │  (REPL text interface) │  │  Thymeleaf MVC · WebSocket (STOMP) │
        └────────────────────────┘  │  HackerNews REST · Groq REST API   │
                                    └────────────────────────────────────┘
```

### 🧩 Components

| Component | Class | Description |
|---|---|---|
| **Downloader** | `dei.googol.core.Downloader` | Parallel web crawler. Dequeues URLs from the shared queue, parses pages with jsoup, and broadcasts `IndexUpdateMessage` objects to all active Barrels via RMI reliable multicast. |
| **URL Queue** | `dei.googol.core.UrlQueue` | Shared FIFO crawl queue with a Bloom filter for O(1) deduplication. Thread-safe via `ConcurrentLinkedQueue` + `ReentrantLock`. |
| **Index Storage Barrel** | `dei.googol.core.Barrel` | Replicated index server. Holds a thread-safe `InvertedIndex`, saves state to disk on a configurable schedule, and recovers automatically from its snapshot on restart. |
| **RMI Gateway** | `dei.googol.core.Gateway` | Single entry point for all clients. Round-robin load balancing, automatic retry on failure, result aggregation, and top-query tracking. |
| **Spring Boot Web Server** | `dei.googol.web` | Full MVC web interface backed by `GatewayService`. Search, URL submission, backlink viewer, and live stats dashboard via WebSocket. |

---

## ✨ Key Features

### Core Distributed System
- ✅ Manual URL submission via web form or console client
- ✅ Iterative/recursive crawling of all discovered hyperlinks
- ✅ Multi-term search returning pages that contain **all** terms
- ✅ Results ranked by descending backlink (inbound-link) count
- ✅ Backlink viewer per result
- ✅ Results paginated 10 per page
- ✅ Reliable multicast — all Barrel replicas receive identical updates
- ✅ Service correct with ≥ 1 active Barrel
- ✅ Barrel crash recovery via periodic disk snapshots
- ✅ Barrel failure invisible to clients (Gateway retries automatically)
- ✅ Round-robin load balancing across Barrels
- ✅ Multiple Downloaders run fully in parallel
- ✅ Bloom filter for visited-URL deduplication
- ✅ All config in `application.properties` — no recompilation needed

### Web Interface & Integrations
- ✅ Spring Boot MVC (Thymeleaf, HTML/Java fully separated)
- ✅ All requests routed through the RMI Gateway (never directly to a Barrel)
- ✅ **Real-time stats** via WebSocket (STOMP/SockJS) — top queries, active barrels, index sizes and avg response times updated every 3 s with no polling
- ✅ **HackerNews REST API** — import matching top stories into the index in one click
- ✅ **Groq AI REST API** — contextual analysis paragraph on the results page, powered by `llama-3.1-8b-instant`
- ✅ Backlinks page linked from every search result

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Build | Maven 3 |
| Distributed comms | Java RMI |
| HTML parsing | jsoup 1.19.1 |
| Web framework | Spring Boot 3.4 + Thymeleaf |
| Real-time push | Spring WebSocket (STOMP + SockJS) |
| REST client | Spring `RestTemplate` |
| AI analysis | [Groq API](https://console.groq.com) — `llama-3.1-8b-instant` |
| External data | [HackerNews Firebase API](https://github.com/HackerNews/API) |

---

## 🔑 Setting Up the Groq API Key

The AI contextual analysis feature uses the [Groq API](https://console.groq.com), which has a **free tier** and is significantly faster than OpenAI.

### Step 1 — Create a Groq API key

1. Go to [console.groq.com](https://console.groq.com) and sign up (free)
2. Navigate to **API Keys → Create API Key**
3. Copy the key — it starts with `gsk_…`

### Step 2 — Provide the key to the application

You have two options:

**Option A — Environment variable ✅ recommended**

This keeps the key out of the codebase entirely.

```bash
# Linux / macOS — add to your shell profile (~/.bashrc, ~/.zshrc, etc.)
export GROQ_API_KEY="gsk_your_key_here"

# Windows (PowerShell)
$env:GROQ_API_KEY = "gsk_your_key_here"
```

The `application.properties` already reads it automatically via `${GROQ_API_KEY:}` — no further changes needed.

**Option B — Edit `application.properties` directly**

Open `src/main/resources/application.properties` and set:

```properties
ai.api.key=gsk_your_key_here
```

> ⚠️ **Never commit a real API key to Git.** Prefer Option A when sharing the project. The `.gitignore` already excludes `barrel-snapshots/`; if you use Option B, make sure the key line stays local.

### What if the key is missing?

If `ai.api.key` is blank, the AI analysis section is silently hidden on the results page — everything else continues to work normally.

---

## ⚙️ How to Run

### Prerequisites

- Java 17+
- Maven 3.6+
- Internet access (for the crawler and external APIs)

### 1 — Build

```bash
mvn clean compile
```

### 2 — Start the RMI Registry

```bash
# Linux / macOS
rmiregistry -J-Djava.class.path=target/classes &

# Windows
start rmiregistry -J-Djava.class.path=target/classes
```

### 3 — Start each component in its own terminal

```bash
# URL Queue  (start this first)
mvn exec:java -Dexec.mainClass="dei.googol.core.UrlQueue"

# Barrel 1
mvn exec:java -Dexec.mainClass="dei.googol.core.Barrel" -Dexec.args="Barrel1"

# Barrel 2
mvn exec:java -Dexec.mainClass="dei.googol.core.Barrel" -Dexec.args="Barrel2"

# Downloader 1
mvn exec:java -Dexec.mainClass="dei.googol.core.Downloader" -Dexec.args="Downloader1"

# Downloader 2
mvn exec:java -Dexec.mainClass="dei.googol.core.Downloader" -Dexec.args="Downloader2"

# Gateway
mvn exec:java -Dexec.mainClass="dei.googol.core.Gateway"

# Web Application
mvn spring-boot:run
```

Open **[http://localhost:8080](http://localhost:8080)** in your browser.

### Optional — Console client

```bash
mvn exec:java -Dexec.mainClass="dei.googol.core.Client"
```

### 🖥️ Distributed deployment (two machines)

**Machine 1** — hosts the RMI Registry and core services:
```
rmiregistry  ·  UrlQueue  ·  Barrel1  ·  Downloader1  ·  Gateway  ·  Web Server
```

**Machine 2** — runs additional replicas:
```
Barrel2  ·  Downloader2
```

Before starting anything on Machine 2, set `rmi.host` to Machine 1's local IP in `application.properties`:

```properties
rmi.host=192.168.1.X   # replace with Machine 1's actual IP
```

---

## 🔧 Configuration Reference

All tuneable parameters live in `src/main/resources/application.properties`. No recompilation needed.

| Key | Default | Description |
|---|---|---|
| `rmi.host` | `localhost` | Hostname/IP of the RMI Registry |
| `rmi.port` | `1099` | Port of the RMI Registry |
| `server.port` | `8080` | HTTP port for the web interface |
| `hackernews.api.base-url` | Firebase URL | HackerNews API base endpoint |
| `hackernews.max-stories` | `100` | Max HN stories to inspect per import |
| `ai.api.url` | Groq endpoint | Chat completions URL — any OpenAI-compatible API works |
| `ai.api.key` | `${GROQ_API_KEY:}` | Reads from env variable; leave blank to disable AI |
| `ai.model` | `llama-3.1-8b-instant` | Model identifier |
| `ai.max-tokens` | `250` | Maximum tokens in the AI response |
| `barrel.snapshot.dir` | `./barrel-snapshots` | Directory for index snapshots (excluded from Git) |
| `barrel.snapshot.interval-seconds` | `60` | How often each Barrel saves its state to disk |

---

## 🧪 Software Tests

| # | Test | Expected outcome | Result |
|---|---|---|---|
| 1 | Start 2 Barrels, submit a URL, wait for crawl | Words and URL appear in both Barrels | ✅ Pass |
| 2 | Kill one Barrel during a search | Gateway retries on surviving Barrel; client sees result | ✅ Pass |
| 3 | Restart a Barrel after indexing 100 pages | Barrel loads snapshot; no data loss | ✅ Pass |
| 4 | Multi-word search query | Only URLs containing **all** terms are returned | ✅ Pass |
| 5 | Ranking by backlinks | Pages with more inbound links appear first | ✅ Pass |
| 6 | Pagination | Results in groups of 10 with working Next / Prev | ✅ Pass |
| 7 | WebSocket stats dashboard | Stats update within 3 s, no page refresh required | ✅ Pass |
| 8 | HackerNews import | Matching story URLs are queued and indexed | ✅ Pass |
| 9 | Groq AI analysis | 2–3 sentence analysis appears on the results page | ✅ Pass |
| 10 | Bloom filter deduplication | Same URL never enters the crawl queue twice | ✅ Pass |
| 11 | Concurrent Downloaders | Both process different URLs in parallel without corruption | ✅ Pass |
| 12 | Config without recompile | Changing `rmi.host` in properties takes effect on restart | ✅ Pass |

---

## 📁 Project Structure

```
src/
├── main/
│   ├── java/dei/googol/
│   │   ├── GoogolSpringBootApplication.java   # Spring Boot entry point
│   │   ├── core/
│   │   │   ├── Barrel.java            # Replicated index storage server
│   │   │   ├── BloomFilter.java       # Probabilistic URL deduplication
│   │   │   ├── Client.java            # Console RMI client
│   │   │   ├── Downloader.java        # Parallel web crawler
│   │   │   ├── Gateway.java           # RMI gateway + load balancer
│   │   │   ├── InvertedIndex.java     # Thread-safe index + disk persistence
│   │   │   └── UrlQueue.java          # Shared crawl queue
│   │   ├── rmi/
│   │   │   ├── IBarrel.java           # Barrel remote interface (Javadoc)
│   │   │   ├── IDownloader.java       # Downloader remote interface
│   │   │   ├── IGateway.java          # Gateway remote interface (Javadoc)
│   │   │   ├── IUrlQueue.java         # URL Queue remote interface
│   │   │   └── IndexUpdateMessage.java # Serialisable multicast payload
│   │   └── web/
│   │       ├── config/
│   │       │   └── WebSocketConfig.java        # STOMP/SockJS configuration
│   │       ├── controller/
│   │       │   └── SearchController.java       # All HTTP routes
│   │       └── service/
│   │           ├── AiService.java              # Groq REST API integration
│   │           ├── GatewayService.java         # RMI Gateway proxy for Spring
│   │           ├── HackerNewsService.java      # HackerNews REST integration
│   │           └── StatsWebSocketService.java  # Pushes stats every 3 s via WS
│   └── resources/
│       ├── application.properties
│       └── templates/
│           ├── search.html      # Homepage / search form
│           ├── results.html     # Paginated results + AI analysis + HN import
│           ├── backlinks.html   # Inbound links for a specific URL
│           ├── index.html       # URL submission form
│           └── stats.html       # Live WebSocket stats dashboard
└── test/
    └── java/dei/googol/
        └── GoogolSpringBootApplicationTests.java
```

---

## 📋 Reliable Multicast — How It Works

Each `Downloader` keeps a live list of all active Barrels discovered from the RMI Registry. For every page crawled, it sends an `IndexUpdateMessage` to **all** Barrels sequentially (one-to-many RMI). If a Barrel is unreachable it is immediately removed from the list; a background health-check thread re-adds it within 5 seconds once it comes back.

Result: every reachable Barrel always holds an identical copy of the index. A Barrel that was offline recovers via its disk snapshot and then resumes receiving live updates as normal.

---

## 🔒 Fault Tolerance Summary

| Failure | Recovery |
|---|---|
| Barrel crash | Gateway removes it within 5 s; remaining replicas serve all requests |
| Barrel restart | Loads snapshot → re-registers in RMI → auto-discovered by health-check |
| Gateway restart | `GatewayService` re-looks up the stub every 10 s automatically |
| Downloader crash | Remaining Downloaders continue consuming the shared URL queue |
