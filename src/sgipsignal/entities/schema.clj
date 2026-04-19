(ns sgipsignal.entities.schema
  "Malli schemas for coerced SGIP Signal entities.

  These describe the Clojure-native shape produced by `sgipsignal.entities` coercion:
  namespaced keywords, Instants, Durations.")

;; ---------------------------------------------------------------------------
;; MOER (from /sgipmoer)
;; ---------------------------------------------------------------------------

(def MoerPoint
  "A coerced MOER data point with Instant timestamps.
  Includes :tick/beginning and :tick/end when freq is present."
  [:map
   [:sgipsignal.moer/point-time inst?]
   [:sgipsignal.moer/value number?]
   [:sgipsignal.moer/ba :string]
   [:sgipsignal.moer/version :string]
   [:sgipsignal.moer/freq :int]
   [:tick/beginning {:optional true} inst?]
   [:tick/end {:optional true} inst?]])

(def MoerResponse
  "Coerced MOER response (normalized to vector)."
  [:map
   [:sgipsignal.response/data [:vector MoerPoint]]])

;; ---------------------------------------------------------------------------
;; Forecast (from /sgipforecast)
;; ---------------------------------------------------------------------------

(def ForecastPoint
  "A coerced forecast data point."
  [:map
   [:sgipsignal.forecast/point-time inst?]
   [:sgipsignal.forecast/value number?]
   [:sgipsignal.forecast/ba :string]
   [:sgipsignal.forecast/version :string]
   [:tick/beginning {:optional true} inst?]
   [:tick/end {:optional true} inst?]])

(def ForecastResponse
  "Coerced forecast response."
  [:map
   [:sgipsignal.response/data [:vector ForecastPoint]]
   [:sgipsignal.response/generated-at inst?]])

;; ---------------------------------------------------------------------------
;; Long forecast (from /sgiplongforecast)
;; ---------------------------------------------------------------------------

(def LongForecastPoint
  "A coerced long forecast data point with percentile bands."
  [:map
   [:sgipsignal.long-forecast/point-time inst?]
   [:sgipsignal.long-forecast/percentile-15th number?]
   [:sgipsignal.long-forecast/percentile-85th number?]
   [:sgipsignal.long-forecast/time-of-day :string]
   [:sgipsignal.long-forecast/ba :string]
   [:sgipsignal.long-forecast/version :string]])

(def LongForecastResponse
  "Coerced long forecast response."
  [:map
   [:sgipsignal.response/data [:vector LongForecastPoint]]])
