(ns sgipsignal.entities
  "Coercion from raw SGIP Signal API responses to namespaced Clojure entities.

  Raw layer: snake_case keys, string values — direct from the API JSON.
  Coerced layer: namespaced keywords, Instants, Durations.

  Every coerced entity preserves the original raw data as :sgipsignal/raw metadata.

  API response shapes (from live API):

  /sgipmoer (current):  single object {moer, point_time, freq, version, ba}
  /sgipmoer (range):    array [{moer, point_time, freq, version, ba}, ...]
  /sgipforecast:        {generated_at, forecast: [{value, point_time, version, ba}, ...]}
  /sgiplongforecast:    {forecast: [{15th_percentile, 85th_percentile, point_time, time_of_day, ba, version}, ...]}"
  (:import [java.time Duration Instant]
           [java.time.temporal Temporal]))

;; ---------------------------------------------------------------------------
;; Parsing helpers
;; ---------------------------------------------------------------------------

(defn- parse-instant
  "Parse an ISO 8601 datetime string to a UTC Instant.
  Handles both Z-suffix and +00:00 offset formats."
  ^Instant [^String s]
  (.toInstant (java.time.OffsetDateTime/parse s)))

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

  When `freq` is present (seconds), assocs :tick/beginning and :tick/end.
  Returns a namespaced map with :sgipsignal/raw metadata."
  [raw]
  (let [point-time (parse-instant (:point_time raw))
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
                 :tick/end (.plus ^Temporal point-time ^Duration period)))
        (with-meta {:sgipsignal/raw raw}))))

;; ---------------------------------------------------------------------------
;; Coercion: Forecast data point (from /sgipforecast)
;; ---------------------------------------------------------------------------

(defn ->forecast-point
  "Coerce a raw forecast data point from /sgipforecast.

  Raw shape: {value, point_time, version, ba}
  Returns a namespaced map with :sgipsignal/raw metadata."
  ([raw]
   (->forecast-point raw nil))
  ([raw ^Duration period]
   (let [point-time (parse-instant (:point_time raw))]
     (-> (cond-> {:sgipsignal.forecast/point-time point-time
                  :sgipsignal.forecast/value      (:value raw)
                  :sgipsignal.forecast/ba         (:ba raw)
                  :sgipsignal.forecast/version    (:version raw)}
           period
           (assoc :tick/beginning point-time
                  :tick/end (.plus ^Temporal point-time period)))
         (with-meta {:sgipsignal/raw raw})))))

;; ---------------------------------------------------------------------------
;; Coercion: Long forecast data point (from /sgiplongforecast)
;; ---------------------------------------------------------------------------

(defn ->long-forecast-point
  "Coerce a raw long forecast data point from /sgiplongforecast.

  Raw shape: {15th_percentile, 85th_percentile, point_time, time_of_day, ba, version}
  Returns a namespaced map with :sgipsignal/raw metadata."
  [raw]
  (let [point-time (parse-instant (:point_time raw))]
    (-> {:sgipsignal.long-forecast/point-time       point-time
         :sgipsignal.long-forecast/percentile-15th  (keyword (str (:15th_percentile raw)))
         :sgipsignal.long-forecast/percentile-85th  (keyword (str (:85th_percentile raw)))
         :sgipsignal.long-forecast/time-of-day      (:time_of_day raw)
         :sgipsignal.long-forecast/ba               (:ba raw)
         :sgipsignal.long-forecast/version          (:version raw)}
        ;; Store the numeric values directly, not as keywords
        (assoc :sgipsignal.long-forecast/percentile-15th (:15th_percentile raw)
               :sgipsignal.long-forecast/percentile-85th (:85th_percentile raw))
        (with-meta {:sgipsignal/raw raw}))))

;; ---------------------------------------------------------------------------
;; Coercion: MOER response (/sgipmoer)
;; ---------------------------------------------------------------------------

(defn ->moer-response
  "Coerce a raw /sgipmoer response.

  The API returns either:
  - A single object (current MOER, no time range params)
  - An array of objects (historical, with starttime/endtime)

  Normalizes both to a vector of coerced moer points."
  [raw]
  (let [points (if (sequential? raw) raw [raw])]
    (-> {:sgipsignal.response/data (mapv ->moer-point points)}
        (with-meta {:sgipsignal/raw raw}))))

;; ---------------------------------------------------------------------------
;; Coercion: Forecast response (/sgipforecast)
;; ---------------------------------------------------------------------------

(defn ->forecast-response
  "Coerce a raw /sgipforecast response.

  Raw shape: {generated_at, forecast: [{value, point_time, version, ba}, ...]}
  Infers period from consecutive data points (typically 5 minutes).
  Attaches :sgipsignal/raw metadata."
  [raw]
  (let [forecast-points (:forecast raw)
        ;; Infer period from first two points if available
        period (when (>= (count forecast-points) 2)
                 (let [t1 (parse-instant (:point_time (first forecast-points)))
                       t2 (parse-instant (:point_time (second forecast-points)))]
                   (Duration/between t1 t2)))]
    (-> {:sgipsignal.response/data         (mapv #(->forecast-point % period) forecast-points)
         :sgipsignal.response/generated-at (parse-instant (:generated_at raw))}
        (with-meta {:sgipsignal/raw raw}))))

;; ---------------------------------------------------------------------------
;; Coercion: Long forecast response (/sgiplongforecast)
;; ---------------------------------------------------------------------------

(defn ->long-forecast-response
  "Coerce a raw /sgiplongforecast response.

  Raw shape: {forecast: [{15th_percentile, 85th_percentile, point_time, time_of_day, ba, version}, ...]}
  Attaches :sgipsignal/raw metadata."
  [raw]
  (-> {:sgipsignal.response/data (mapv ->long-forecast-point (:forecast raw))}
      (with-meta {:sgipsignal/raw raw})))
