# clj-sgipsignal

Clojure client for the [SGIP Signal API](https://sgipsignal.com) — California's publicly available marginal greenhouse gas emissions signal.

## Overview

The SGIP (Self-Generation Incentive Program) Signal provides real-time and forecasted Marginal Operating Emissions Rate (MOER) data for California and neighboring grid regions. The signal is free, publicly available, and can be redistributed — making it suitable for integration into grid coordination services like [clj-price-server](https://github.com/grid-coordination/clj-price-server).

## Installation

Add to your `deps.edn`:

```clojure
energy.grid-coordination/clj-sgipsignal {:mvn/version "0.1.0"}
```

## Quick Start

```clojure
(require '[sgipsignal.client :as sgip])

;; Create a client (credentials from env vars SGIP_USER / SGIP_PASSWORD)
(def client (sgip/make-client))

;; Get current MOER for PG&E territory
(sgip/moer* client {:ba "SGIP_CAISO_PGE"})

;; Get 72-hour forecast
(sgip/forecast* client {:ba "SGIP_CAISO_SCE"})

;; Get long-term forecast (month or year horizon)
(sgip/long-forecast* client {:ba "SGIP_CAISO_SDGE" :horizon "month"})
```

## Regions

| Code | Region |
|------|--------|
| `SGIP_CAISO_PGE` | Pacific Gas & Electric |
| `SGIP_CAISO_SCE` | Southern California Edison |
| `SGIP_CAISO_SDGE` | San Diego Gas & Electric |
| `SGIP_LADWP` | Los Angeles DWP |
| `SGIP_BANC_SMUD` | Sacramento Municipal Utility District |
| `SGIP_BANC_P2` | Balancing Authority of Northern California |
| `SGIP_IID` | Imperial Irrigation District |
| `SGIP_PACW` | PacifiCorp West |
| `SGIP_NVENERGY` | NV Energy |
| `SGIP_TID` | Turlock Irrigation District |
| `SGIP_WALC` | Western Area Lower Colorado |

## License

Copyright (c) 2026 Clark Communications Corporation. MIT License.
