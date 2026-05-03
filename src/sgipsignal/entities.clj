(ns sgipsignal.entities
  "Coercion from raw SGIP Signal API responses to namespaced Clojure entities.

  Raw layer: snake_case keys, string values — direct from the API JSON.
  Coerced layer: namespaced keywords, ZonedDateTimes, Durations.

  Time handling: the SGIP Signal wire is always UTC (the `/sgipmoer`
  endpoint emits `Z` form, `/sgipforecast` emits `+00:00` form — both
  parse cleanly via `OffsetDateTime/parse`). Coerced timestamps are
  `ZonedDateTime` values expressed in the caller-supplied `zone`, so
  the same UTC instant can be presented in any zone the consumer
  prefers (default UTC). Returning `ZonedDateTime` instead of `Instant`
  brings clj-sgipsignal into parity with peer libraries (clj-gridx,
  clj-urpx, clj-urdb) that all expose ZonedDateTime end-to-end.

  Every coerced entity preserves the original raw data as :sgipsignal/raw
  metadata.

  API response shapes (from live API):

  /sgipmoer (current):  single object {moer, point_time, freq, version, ba}
  /sgipmoer (range):    array [{moer, point_time, freq, version, ba}, ...]
  /sgipforecast:        {generated_at, forecast: [{value, point_time, version, ba}, ...]}
  /sgiplongforecast:    {forecast: [{15th_percentile, 85th_percentile, point_time, time_of_day, ba, version}, ...]}"
  (:import [java.time Duration OffsetDateTime ZoneId ZoneOffset ZonedDateTime]))

;; ---------------------------------------------------------------------------
;; Parsing helpers
;; ---------------------------------------------------------------------------

(def ^:private utc ZoneOffset/UTC)

(defn- ->zone-id
  "Coerce a `ZoneId`, a zone-id string (e.g. \"America/Los_Angeles\"), or
  nil to a `ZoneId`. Nil yields UTC."
  ^ZoneId [zone]
  (cond
    (nil? zone)             utc
    (instance? ZoneId zone) zone
    (string? zone)          (ZoneId/of zone)
    :else                   (throw (ex-info "Invalid :zone — expected ZoneId or zone-id string"
                                            {:zone zone :type (type zone)}))))

(defn- parse-zoned-datetime
  "Parse an SGIP Signal timestamp (always UTC on the wire — either
  `Z` or `+00:00` form) into a `ZonedDateTime` expressed in `zone`.

  The wire string fixes the UTC instant via `OffsetDateTime/parse`;
  `.atZoneSameInstant` then re-expresses that instant in `zone`,
  yielding a `ZonedDateTime` that knows the zone's DST rules. The
  underlying instant is unchanged — only the wall-clock presentation
  changes.

  No offset sanity check is needed (unlike clj-gridx): the wire is
  always UTC by API contract, so any caller-supplied zone is valid as
  a presentation choice."
  ^ZonedDateTime [^String s ^ZoneId zone]
  (.atZoneSameInstant (OffsetDateTime/parse s) zone))

(defn- seconds->duration
  "Convert seconds (integer) to a java.time.Duration."
  ^Duration [seconds]
  (Duration/ofSeconds seconds))

;; ---------------------------------------------------------------------------
;; Coercion: MOER data point (from /sgipmoer)
;; ---------------------------------------------------------------------------

(defn ->moer-point
  "Coerce a raw MOER data point from /sgipmoer.

  Raw shape: {moer, point_time, freq, version, ba}
  Note: `moer` may be a string or number depending on the endpoint response.

  `zone` is a `ZoneId` (or zone-id string) used to express the timestamp.
  Defaults to UTC. When `freq` is present (seconds), assocs :tick/beginning
  and :tick/end as ZonedDateTimes. Returns a namespaced map with
  :sgipsignal/raw metadata."
  ([raw] (->moer-point raw nil))
  ([raw zone]
   (let [zone-id    (->zone-id zone)
         point-time (parse-zoned-datetime (:point_time raw) zone-id)
         moer-val   (let [v (:moer raw)] (if (string? v) (parse-double v) v))
         freq       (:freq raw)
         period     (when freq (seconds->duration freq))]
     (-> (cond-> {:sgipsignal.moer/point-time point-time
                  :sgipsignal.moer/value      moer-val
                  :sgipsignal.moer/ba         (:ba raw)
                  :sgipsignal.moer/version    (:version raw)
                  :sgipsignal.moer/freq       freq}
           period
           (assoc :tick/beginning point-time
                  :tick/end (.plus point-time period)))
         (with-meta {:sgipsignal/raw raw})))))

;; ---------------------------------------------------------------------------
;; Coercion: Forecast data point (from /sgipforecast)
;; ---------------------------------------------------------------------------

(defn ->forecast-point
  "Coerce a raw forecast data point from /sgipforecast.

  Raw shape: {value, point_time, version, ba}
  `zone` is a `ZoneId` (or zone-id string) used to express the timestamp.
  Defaults to UTC. Returns a namespaced map with :sgipsignal/raw metadata."
  ([raw] (->forecast-point raw nil nil))
  ([raw period] (->forecast-point raw period nil))
  ([raw ^Duration period zone]
   (let [zone-id    (->zone-id zone)
         point-time (parse-zoned-datetime (:point_time raw) zone-id)]
     (-> (cond-> {:sgipsignal.forecast/point-time point-time
                  :sgipsignal.forecast/value      (:value raw)
                  :sgipsignal.forecast/ba         (:ba raw)
                  :sgipsignal.forecast/version    (:version raw)}
           period
           (assoc :tick/beginning point-time
                  :tick/end (.plus point-time period)))
         (with-meta {:sgipsignal/raw raw})))))

;; ---------------------------------------------------------------------------
;; Coercion: Long forecast data point (from /sgiplongforecast)
;; ---------------------------------------------------------------------------

(defn ->long-forecast-point
  "Coerce a raw long forecast data point from /sgiplongforecast.

  Raw shape: {15th_percentile, 85th_percentile, point_time, time_of_day, ba, version}
  `zone` is a `ZoneId` (or zone-id string) used to express the timestamp.
  Defaults to UTC. Returns a namespaced map with :sgipsignal/raw metadata."
  ([raw] (->long-forecast-point raw nil))
  ([raw zone]
   (let [zone-id    (->zone-id zone)
         point-time (parse-zoned-datetime (:point_time raw) zone-id)]
     (-> {:sgipsignal.long-forecast/point-time      point-time
          :sgipsignal.long-forecast/percentile-15th (:15th_percentile raw)
          :sgipsignal.long-forecast/percentile-85th (:85th_percentile raw)
          :sgipsignal.long-forecast/time-of-day     (:time_of_day raw)
          :sgipsignal.long-forecast/ba              (:ba raw)
          :sgipsignal.long-forecast/version         (:version raw)}
         (with-meta {:sgipsignal/raw raw})))))

;; ---------------------------------------------------------------------------
;; Coercion: MOER response (/sgipmoer)
;; ---------------------------------------------------------------------------

(defn ->moer-response
  "Coerce a raw /sgipmoer response.

  The API returns either:
  - A single object (current MOER, no time range params)
  - An array of objects (historical, with starttime/endtime)

  Normalizes both to a vector of coerced moer points, with timestamps
  expressed in `zone` (default UTC)."
  ([raw] (->moer-response raw nil))
  ([raw zone]
   (let [zone-id (->zone-id zone)
         points  (if (sequential? raw) raw [raw])]
     (-> {:sgipsignal.response/data (mapv #(->moer-point % zone-id) points)}
         (with-meta {:sgipsignal/raw raw})))))

;; ---------------------------------------------------------------------------
;; Coercion: Forecast response (/sgipforecast)
;; ---------------------------------------------------------------------------

(defn ->forecast-response
  "Coerce a raw /sgipforecast response.

  Raw shape: {generated_at, forecast: [{value, point_time, version, ba}, ...]}
  Infers period from consecutive data points (typically 5 minutes).
  Timestamps are expressed in `zone` (default UTC).
  Attaches :sgipsignal/raw metadata."
  ([raw] (->forecast-response raw nil))
  ([raw zone]
   (let [zone-id         (->zone-id zone)
         forecast-points (:forecast raw)
         period          (when (>= (count forecast-points) 2)
                           (let [t1 (parse-zoned-datetime (:point_time (first forecast-points)) zone-id)
                                 t2 (parse-zoned-datetime (:point_time (second forecast-points)) zone-id)]
                             (Duration/between t1 t2)))]
     (-> {:sgipsignal.response/data         (mapv #(->forecast-point % period zone-id) forecast-points)
          :sgipsignal.response/generated-at (parse-zoned-datetime (:generated_at raw) zone-id)}
         (with-meta {:sgipsignal/raw raw})))))

;; ---------------------------------------------------------------------------
;; Coercion: Long forecast response (/sgiplongforecast)
;; ---------------------------------------------------------------------------

(defn ->long-forecast-response
  "Coerce a raw /sgiplongforecast response.

  Raw shape: {forecast: [{15th_percentile, 85th_percentile, point_time, time_of_day, ba, version}, ...]}
  Timestamps are expressed in `zone` (default UTC).
  Attaches :sgipsignal/raw metadata."
  ([raw] (->long-forecast-response raw nil))
  ([raw zone]
   (let [zone-id (->zone-id zone)]
     (-> {:sgipsignal.response/data (mapv #(->long-forecast-point % zone-id) (:forecast raw))}
         (with-meta {:sgipsignal/raw raw})))))
