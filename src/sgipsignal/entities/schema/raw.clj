(ns sgipsignal.entities.schema.raw
  "Malli schemas for the raw SGIP Signal API response shapes.

  These mirror the JSON exactly: snake_case keys, string/number values.
  Most consumers should use `sgipsignal.entities.schema` (the coerced schemas)
  instead — these are primarily useful for boundary validation.")

;; ---------------------------------------------------------------------------
;; /sgipmoer
;; ---------------------------------------------------------------------------

(def MoerPoint
  "A single MOER data point from /sgipmoer.
  Note: `moer` may be a string or number depending on the response."
  [:map
   [:point_time :string]
   [:moer [:or :string number?]]
   [:freq :int]
   [:version :string]
   [:ba :string]])

(def MoerCurrentResponse
  "Current MOER response (single object, no time range)."
  MoerPoint)

(def MoerHistoricalResponse
  "Historical MOER response (array, with starttime/endtime)."
  [:vector MoerPoint])

;; ---------------------------------------------------------------------------
;; /sgipforecast
;; ---------------------------------------------------------------------------

(def ForecastPoint
  "A single forecast data point."
  [:map
   [:point_time :string]
   [:value number?]
   [:version :string]
   [:ba :string]])

(def ForecastResponse
  "Forecast response from /sgipforecast."
  [:map
   [:generated_at :string]
   [:forecast [:vector ForecastPoint]]])

;; ---------------------------------------------------------------------------
;; /sgiplongforecast
;; ---------------------------------------------------------------------------

(def LongForecastPoint
  "A single long forecast data point with percentile bands."
  [:map
   [:15th_percentile number?]
   [:85th_percentile number?]
   [:point_time :string]
   [:time_of_day :string]
   [:ba :string]
   [:version :string]])

(def LongForecastResponse
  "Long forecast response from /sgiplongforecast."
  [:map
   [:forecast [:vector LongForecastPoint]]])

;; ---------------------------------------------------------------------------
;; Auth
;; ---------------------------------------------------------------------------

(def LoginResponse
  [:map
   [:token :string]])

(def RegisterResponse
  [:map
   [:ok {:optional true} :string]
   [:user {:optional true} :string]
   [:error {:optional true} :string]])
