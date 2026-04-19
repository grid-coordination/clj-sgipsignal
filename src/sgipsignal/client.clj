(ns sgipsignal.client
  "Stateful SGIP Signal client composing auth and rate limiting.

  This is the primary entry point for most consumers. Create a client
  with `make-client`, then call the convenience functions which handle
  authentication and rate limiting automatically.

  The client is a plain map, not a Component — wrap it in a Component
  in your application if needed."
  (:require [sgipsignal.api :as api]
            [sgipsignal.auth :as auth]
            [sgipsignal.entities :as entities]
            [sgipsignal.rate-limit :as rl]))

;; ---------------------------------------------------------------------------
;; Client creation
;; ---------------------------------------------------------------------------

(defn make-client
  "Create an SGIP Signal client with automatic auth and rate limiting.

  Options:
    :username       — SGIP Signal username (or env SGIP_USER)
    :password       — SGIP Signal password (or env SGIP_PASSWORD)
    :base-url       — API base URL (default https://sgipsignal.com)
    :max-per-second — Rate limit (default 10)
    :user-agent     — Custom User-Agent string"
  ([] (make-client {}))
  ([opts]
   (let [auth-mgr (auth/create-auth opts)
         limiter  (rl/create-limiter (select-keys opts [:max-per-second]))]
     {:auth       auth-mgr
      :limiter    limiter
      :base-url   (or (:base-url opts) api/default-base-url)
      :user-agent (:user-agent opts)})))

(defn- client-cfg
  "Build an API config map from a client, obtaining a fresh token."
  [client]
  {:token      (auth/token (:auth client))
   :base-url   (:base-url client)
   :user-agent (:user-agent client)})

(defn- rate-limited-call
  "Execute an API call with rate limiting."
  [client api-fn params]
  (rl/acquire! (:limiter client))
  (api-fn (client-cfg client) params))

;; ---------------------------------------------------------------------------
;; Raw API access (returns HTTP responses)
;; ---------------------------------------------------------------------------

(defn moer
  "Get real-time and historical MOER data.
  params: {:ba \"SGIP_CAISO_PGE\" :starttime ... :endtime ...}"
  [client params]
  (rate-limited-call client api/moer params))

(defn forecast
  "Get 72-hour forecast.
  params: {:ba \"SGIP_CAISO_PGE\" :starttime ... :endtime ...}"
  [client params]
  (rate-limited-call client api/forecast params))

(defn long-forecast
  "Get long-term forecast (month or year horizon).
  params: {:ba \"SGIP_CAISO_PGE\" :horizon \"month\"}"
  [client params]
  (rate-limited-call client api/long-forecast params))

;; ---------------------------------------------------------------------------
;; Coerced entity access (returns namespaced entities with :sgipsignal/raw metadata)
;; ---------------------------------------------------------------------------

(defn moer*
  "Like `moer` but returns a coerced MoerResponse entity.
  Normalizes both single-object and array responses to a vector."
  [client params]
  (let [resp (moer client params)]
    (when (api/success? resp)
      (entities/->moer-response (api/body resp)))))

(defn forecast*
  "Like `forecast` but returns a coerced ForecastResponse entity."
  [client params]
  (let [resp (forecast client params)]
    (when (api/success? resp)
      (entities/->forecast-response (api/body resp)))))

(defn long-forecast*
  "Like `long-forecast` but returns a coerced LongForecastResponse entity."
  [client params]
  (let [resp (long-forecast client params)]
    (when (api/success? resp)
      (entities/->long-forecast-response (api/body resp)))))
