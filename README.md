# clj-sgipsignal

A Clojure client library for the [SGIP Signal API](https://sgipsignal.com) — California's publicly available marginal greenhouse gas emissions signal.

[![Clojars](https://img.shields.io/clojars/v/energy.grid-coordination/clj-sgipsignal.svg)](https://clojars.org/energy.grid-coordination/clj-sgipsignal)
[![Lint & Test](https://github.com/grid-coordination/clj-sgipsignal/actions/workflows/lint-test.yml/badge.svg)](https://github.com/grid-coordination/clj-sgipsignal/actions/workflows/lint-test.yml)
[![md-docs](https://img.shields.io/badge/md--docs-included-green)](https://github.com/dcj/codox-md)
[![build-provenance](https://img.shields.io/badge/build--provenance-included-blue)](https://github.com/dcj/build-provenance)

## Overview

The SGIP (Self-Generation Incentive Program) Signal provides real-time and forecasted Marginal Operating Emissions Rate (MOER) data for California and neighboring grid regions. The signal is operated by [WattTime](https://watttime.org/) on behalf of the California Public Utilities Commission and covers 11 balancing authority regions.

Unlike the WattTime MOER API (which restricts redistribution of raw data), the SGIP Signal is **free, publicly available, and redistributable** — making it suitable for integration into grid coordination services that republish emissions data, such as OpenADR 3 VTNs.

**Units**: MOER values are in **kg CO2/kWh**. Each 5-minute interval timestamp is valid for that period. Values are typically available 2-3 minutes before the timestamp for which they are valid.

See the [SGIP Signal API documentation](https://docs.sgipsignal.com/) for full details.

## Features

- **Raw API access** — stateless HTTP functions returning full hato responses
- **Entity coercion** — raw snake_case JSON responses coerced to namespaced Clojure maps with Instants, Durations, and tick intervals (following the [clj-oa3](https://github.com/grid-coordination/clj-oa3) entity pattern)
- **Malli schemas** — two-layer validation: raw API shapes and coerced entity shapes
- **JWT auth management** — automatic login and token refresh (30-minute tokens, refresh at 25 minutes)
- **Rate limiting** — sliding-window rate limiter (default 10 req/s per [API restrictions](https://docs.sgipsignal.com/#tag/Introduction/Restrictions): 3,000 requests per 5-minute rolling window)
- **Stateful client** — composes auth + rate limiting for convenient use

## Getting an Account

The SGIP Signal API requires a username and password. Registration is done via the API itself (there is no web form). Passwords must be at least 8 characters with alpha, numeric, and special characters:

```bash
curl -X POST https://sgipsignal.com/register \
  -H "Content-Type: application/json" \
  -d '{"username": "your_username",
       "password": "your_password",
       "email": "you@example.com",
       "org": "Your Organization"}'
```

On success you'll get `{"ok": "User created", "user": "your_username"}`. You can then use those credentials with this library.

For questions about API access, contact **SGIP@WattTime.org**.

## Installation

Add to your `deps.edn`:

```clojure
energy.grid-coordination/clj-sgipsignal {:mvn/version "0.2.0"}
```

Or use a git dependency:

```clojure
io.github.grid-coordination/clj-sgipsignal
  {:git/sha "..."}
```

## Quick Start

```clojure
(require '[sgipsignal.client :as sgip])

;; Create a client (credentials from env vars SGIP_USER / SGIP_PASSWORD)
(def client (sgip/make-client))

;; Or provide credentials explicitly
(def client (sgip/make-client {:username "myuser" :password "mypass"}))

;; Get current MOER for PG&E territory
(sgip/moer* client {:ba "SGIP_CAISO_PGE"})
;; => {:sgipsignal.response/data
;;      [{:sgipsignal.moer/point-time #inst "2026-04-19T14:55:00Z",
;;        :sgipsignal.moer/value 0.0,
;;        :sgipsignal.moer/ba "SGIP_CAISO_PGE",
;;        :sgipsignal.moer/version "2.0",
;;        :sgipsignal.moer/freq 300,
;;        :tick/beginning #inst "2026-04-19T14:55:00Z",
;;        :tick/end #inst "2026-04-19T15:00:00Z"}]}

;; Get 72-hour forecast
(sgip/forecast* client {:ba "SGIP_CAISO_SCE"})

;; Get long-term forecast (month or year horizon)
(sgip/long-forecast* client {:ba "SGIP_CAISO_SDGE" :horizon "month"})
```

## API Coverage

All [SGIP Signal API](https://docs.sgipsignal.com/) data endpoints are supported:

| Function | Endpoint | Description |
|----------|----------|-------------|
| `moer` | `GET /sgipmoer` | Real-time and historical MOER data |
| `forecast` | `GET /sgipforecast` | 72-hour MOER forecast |
| `long-forecast` | `GET /sgiplongforecast` | Long-term forecast (month or year horizon) |

Each function has a coerced variant (suffixed with `*`) that returns namespaced entities instead of raw HTTP responses.

### Parameters

All data endpoints require `:ba` (balancing authority). Optional parameters:

| Parameter | Endpoints | Description |
|-----------|-----------|-------------|
| `:ba` | all | Balancing authority region code (required) |
| `:starttime` | all | Start of time range (ISO 8601) |
| `:endtime` | all | End of time range (ISO 8601) |
| `:version` | `moer`, `forecast` | Model version (see below) |
| `:horizon` | `long-forecast` | `"month"` or `"year"` (required for long-forecast) |

### Query Constraints

- **MOER historical**: maximum 31-day span per query
- **Forecast historical**: maximum 1-day span; `starttime`/`endtime` refer to when the forecast was *generated*, not the forecasted period. 1 year of historical forecasts available for CAISO regions.
- **Long forecast**: year horizon gives monthly frequency; month horizon gives daily frequency. Each point includes 15th/85th percentile MOER values and a time-of-day label (morning, day, evening, night in PST).

### Model Versions

| MOER Version | Forecast Version | Valid Dates |
|--------------|------------------|-------------|
| `1.0` | `1.0-1.0.0` | April 1, 2020 -- December 31, 2021 |
| `2.0` | `2.0-1.0.0` | January 1, 2022 onward |

### Rate Limits

Per the [API restrictions](https://docs.sgipsignal.com/#tag/Introduction/Restrictions):

- **Data endpoints**: 3,000 requests per 5-minute rolling window (average 10 req/s)
- **Login endpoint**: 100 requests per 5 minutes
- Exceeding limits returns HTTP 429

The client's built-in rate limiter defaults to 10 req/s to stay within these bounds.

## Architecture

The library is organized in five layers, each usable independently:

```
sgipsignal.client        <- Stateful client (most users start here)
  |-- sgipsignal.auth        <- JWT token management
  |-- sgipsignal.rate-limit  <- Sliding-window rate limiter
  |-- sgipsignal.api         <- Raw HTTP functions (stateless)
  `-- sgipsignal.entities    <- Coercion: raw JSON -> namespaced entities
        |-- sgipsignal.entities.schema      <- Malli schemas (coerced)
        `-- sgipsignal.entities.schema.raw  <- Malli schemas (raw API)
```

### Layer 1: `sgipsignal.api` — Raw HTTP

Stateless functions taking a config map `{:token "..." :base-url "..."}` and returning hato responses. No auth management, no rate limiting.

```clojure
(require '[sgipsignal.api :as api])

;; Login to get a token
(def resp (api/login {:username "user" :password "pass"}))
(def token (get-in resp [:body :token]))

;; Use the token for data requests
(api/moer {:token token} {:ba "SGIP_CAISO_PGE"})
;; => {:status 200, :body {:moer "0.0", :point_time "...", ...}, ...}
```

Also available: `register`, `password-reset`, `forecast`, `long-forecast`.

### Layer 2: `sgipsignal.entities` — Coercion

Transforms raw API responses into namespaced Clojure entities. Every coerced entity carries the original raw data as `:sgipsignal/raw` metadata.

```clojure
(require '[sgipsignal.entities :as entities])

(def raw-response (:body (api/moer cfg {:ba "SGIP_CAISO_PGE"})))
(def coerced (entities/->moer-response raw-response))

(:sgipsignal.response/data coerced)
;; => [{:sgipsignal.moer/point-time #inst "2026-04-19T14:55:00Z"
;;      :sgipsignal.moer/value 0.0
;;      :sgipsignal.moer/ba "SGIP_CAISO_PGE"
;;      :sgipsignal.moer/freq 300
;;      :tick/beginning #inst "2026-04-19T14:55:00Z"
;;      :tick/end #inst "2026-04-19T15:00:00Z"} ...]

;; Access original raw data
(:sgipsignal/raw (meta coerced))
;; => {:moer "0.0", :point_time "2026-04-19T14:55:00Z", :freq 300, ...}
```

### Layer 3: `sgipsignal.auth` — Token Management

```clojure
(require '[sgipsignal.auth :as auth])

(def auth-mgr (auth/create-auth {:username "user" :password "pass"}))

;; Get a valid token (auto-refreshes when < 5 min remaining)
(auth/token auth-mgr) ;; => "eyJ..."
```

### Layer 4: `sgipsignal.rate-limit` — Rate Limiter

Composable sliding-window rate limiter. Usable standalone.

```clojure
(require '[sgipsignal.rate-limit :as rl])

(def limiter (rl/create-limiter {:max-per-second 10}))

;; Block until a slot is available
(rl/acquire! limiter)

;; Or wrap any function
(def rate-limited-f (rl/wrap-rate-limit my-fn limiter))
```

## Entity Types

| Coercion Function | Source Endpoint | Entity Namespace |
|-------------------|-----------------|------------------|
| `->moer-point` | `/sgipmoer` data points | `:sgipsignal.moer/*` |
| `->forecast-point` | `/sgipforecast` data points | `:sgipsignal.forecast/*` |
| `->long-forecast-point` | `/sgiplongforecast` data points | `:sgipsignal.long-forecast/*` |
| `->moer-response` | `/sgipmoer` full response | `:sgipsignal.response/*` |
| `->forecast-response` | `/sgipforecast` full response | `:sgipsignal.response/*` |
| `->long-forecast-response` | `/sgiplongforecast` full response | `:sgipsignal.response/*` |

### Tick Intervals

MOER points get tick intervals from their `freq` field (typically 300s = 5 minutes). Forecast points get tick intervals inferred from consecutive data point spacing. This makes each data point a [tick](https://github.com/juxt/tick) interval, enabling Allen's interval algebra:

```clojure
(require '[tick.core :as t])

(let [dp (first (:sgipsignal.response/data coerced))]
  (t/contains? dp some-instant)     ;=> true/false
  (t/relation dp other-interval))   ;=> :meets, :overlaps, etc.
```

### Long Forecast Time-of-Day Periods

Long forecasts label each data point with a `time-of-day` value (Pacific Standard Time):

| Period | PST Hours |
|--------|-----------|
| Morning | 06:00 -- 11:55 |
| Day | 12:00 -- 15:55 |
| Evening | 16:00 -- 20:55 |
| Night | 21:00 -- 05:55 |

## Regions

The SGIP Signal covers 11 grid regions across California and neighboring areas:

| Code | Region |
|------|--------|
| `SGIP_CAISO_PGE` | Pacific Gas & Electric (CAISO DLAP) |
| `SGIP_CAISO_SCE` | Southern California Edison (CAISO DLAP) |
| `SGIP_CAISO_SDGE` | San Diego Gas & Electric (CAISO DLAP) |
| `SGIP_LADWP` | Los Angeles Department of Water & Power |
| `SGIP_BANC_SMUD` | Sacramento Municipal Utility District |
| `SGIP_BANC_P2` | Balancing Authority of Northern California |
| `SGIP_IID` | Imperial Irrigation District |
| `SGIP_PACW` | PacifiCorp West |
| `SGIP_NVENERGY` | NV Energy |
| `SGIP_TID` | Turlock Irrigation District |
| `SGIP_WALC` | Western Area Lower Colorado |

Community Choice Aggregators (CCAs) use the grid region of their underlying investor-owned utility.

## Configuration

### Environment Variables

| Variable | Description |
|----------|-------------|
| `SGIP_USER` | SGIP Signal username |
| `SGIP_PASSWORD` | SGIP Signal password |

### Client Options

```clojure
(sgip/make-client
  {:username       "user"                    ;; or env SGIP_USER
   :password       "pass"                    ;; or env SGIP_PASSWORD
   :base-url       "https://sgipsignal.com"  ;; default
   :max-per-second 10                        ;; rate limit, default 10
   :user-agent     "my-app/1.0"})            ;; custom User-Agent
```

## Development

```bash
# Start nREPL (port written to .nrepl-port)
clojure -M:nrepl

# Run unit tests
clojure -M:test

# Run integration tests (requires SGIP_USER / SGIP_PASSWORD env vars)
clojure -M:test-integration

# Lint
clj-kondo --lint src test test-integration

# Build JAR (runs unit tests first)
clojure -T:build ci

# Install locally
clojure -T:build install

# Deploy to Clojars
clojure -T:build deploy
```

## Bulk Historical Data

Monthly CSV exports of historical MOER data are available for all regions at `data.sgipsignal.com`, no authentication required:

```bash
curl -O https://data.sgipsignal.com/historical/SGIP_CAISO_PGE.zip
```

See the [download page](https://content.sgipsignal.com/download-data/) for all regions. This library does not wrap these downloads — they are static files outside the API.

## Relationship to clj-watttime

WattTime operates two emissions signal APIs:

| | SGIP Signal (this library) | WattTime API ([clj-watttime](https://github.com/grid-coordination/clj-watttime)) |
|---|---|---|
| **URL** | sgipsignal.com | api.watttime.org |
| **Docs** | [docs.sgipsignal.com](https://docs.sgipsignal.com/) | [docs.watttime.org](https://docs.watttime.org/) |
| **Access** | Free, public | Requires researcher/commercial plan |
| **Redistribution** | Allowed | Restricted |
| **Coverage** | 11 CA/western regions | Global |
| **Signal types** | MOER only | MOER, AOER, health damage |
| **Use case** | Public apps, OpenADR VTNs | Internal analysis, research |

Both APIs share the same authentication pattern (HTTP Basic -> JWT token) and were built by WattTime. This library mirrors the architecture of clj-watttime.

## License

Copyright (c) 2026 Clark Communications Corporation. All rights reserved.

Distributed under the MIT License. See [LICENSE](LICENSE) for details.
