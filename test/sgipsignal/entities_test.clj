(ns sgipsignal.entities-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [sgipsignal.entities :as entities]
            [sgipsignal.entities.schema :as schema]
            [sgipsignal.entities.schema.raw :as raw]))

;; ---------------------------------------------------------------------------
;; Sample raw API responses (from live SGIP Signal API, 2026-04-19)
;; ---------------------------------------------------------------------------

(def sample-moer-current
  "Current MOER — single flat object (no time range params)."
  {:moer "0.0"
   :point_time "2026-04-19T14:55:00Z"
   :freq 300
   :version "2.0"
   :ba "SGIP_CAISO_PGE"})

(def sample-moer-historical
  "Historical MOER — array of objects (with starttime/endtime)."
  [{:point_time "2026-04-19T01:00:00+00:00"
    :moer 0.5949437933903031
    :version "2.0"
    :freq 300
    :ba "SGIP_CAISO_PGE"}
   {:point_time "2026-04-19T00:55:00+00:00"
    :moer 0.5949437933903031
    :version "2.0"
    :freq 300
    :ba "SGIP_CAISO_PGE"}
   {:point_time "2026-04-19T00:50:00+00:00"
    :moer 0.33968406490609093
    :version "2.0"
    :freq 300
    :ba "SGIP_CAISO_PGE"}])

(def sample-forecast-response
  "Forecast response from /sgipforecast."
  {:generated_at "2026-04-19T15:00:00+00:00"
   :forecast [{:point_time "2026-04-19T15:05:00+00:00"
               :value 0.021803884178157724
               :version "2.0-1.0.0"
               :ba "SGIP_CAISO_PGE"}
              {:point_time "2026-04-19T15:10:00+00:00"
               :value 0.04900305326974199
               :version "2.0-1.0.0"
               :ba "SGIP_CAISO_PGE"}
              {:point_time "2026-04-19T15:15:00+00:00"
               :value 0.0548350262838064
               :version "2.0-1.0.0"
               :ba "SGIP_CAISO_PGE"}]})

(def sample-long-forecast-response
  "Long forecast response from /sgiplongforecast."
  {:forecast [{:15th_percentile 0.03603787033073358
               :85th_percentile 0.1628459292712714
               :ba "SGIP_CAISO_PGE"
               :point_time "2026-04-19T00:00:00+00:00"
               :time_of_day "day"
               :version "2.0-0.0.1"}
              {:15th_percentile 0.20544094812535318
               :85th_percentile 0.4812208696475668
               :ba "SGIP_CAISO_PGE"
               :point_time "2026-04-19T00:00:00+00:00"
               :time_of_day "evening"
               :version "2.0-0.0.1"}]})

;; ---------------------------------------------------------------------------
;; Raw schema validation
;; ---------------------------------------------------------------------------

(deftest raw-schema-validation
  (testing "MoerPoint validates current MOER"
    (is (nil? (m/explain raw/MoerPoint sample-moer-current))))

  (testing "MoerHistoricalResponse validates historical MOER"
    (is (nil? (m/explain raw/MoerHistoricalResponse sample-moer-historical))))

  (testing "ForecastResponse validates sample"
    (is (nil? (m/explain raw/ForecastResponse sample-forecast-response))))

  (testing "LongForecastResponse validates sample"
    (is (nil? (m/explain raw/LongForecastResponse sample-long-forecast-response)))))

;; ---------------------------------------------------------------------------
;; MOER point coercion
;; ---------------------------------------------------------------------------

(deftest moer-point-coercion
  (let [dp (entities/->moer-point (first sample-moer-historical))]
    (testing "point-time is an Instant"
      (is (instance? java.time.Instant (:sgipsignal.moer/point-time dp))))
    (testing "value is a number"
      (is (= 0.5949437933903031 (:sgipsignal.moer/value dp))))
    (testing "ba preserved"
      (is (= "SGIP_CAISO_PGE" (:sgipsignal.moer/ba dp))))
    (testing "version preserved"
      (is (= "2.0" (:sgipsignal.moer/version dp))))
    (testing "freq preserved"
      (is (= 300 (:sgipsignal.moer/freq dp))))
    (testing "raw metadata preserved"
      (is (= (first sample-moer-historical) (:sgipsignal/raw (meta dp)))))
    (testing "tick keys present from freq"
      (is (= (:sgipsignal.moer/point-time dp) (:tick/beginning dp)))
      (is (= (java.time.Instant/parse "2026-04-19T01:05:00Z") (:tick/end dp))))))

(deftest moer-point-string-value
  (testing "moer as string is parsed to number"
    (let [dp (entities/->moer-point sample-moer-current)]
      (is (= 0.0 (:sgipsignal.moer/value dp)))
      (is (number? (:sgipsignal.moer/value dp))))))

;; ---------------------------------------------------------------------------
;; MOER response coercion
;; ---------------------------------------------------------------------------

(deftest moer-response-current
  (testing "single object is normalized to vector"
    (let [resp (entities/->moer-response sample-moer-current)]
      (is (= 1 (count (:sgipsignal.response/data resp))))
      (is (= sample-moer-current (:sgipsignal/raw (meta resp)))))))

(deftest moer-response-historical
  (testing "array is coerced to vector of points"
    (let [resp (entities/->moer-response sample-moer-historical)]
      (is (= 3 (count (:sgipsignal.response/data resp))))
      (is (instance? java.time.Instant
                     (:sgipsignal.moer/point-time (first (:sgipsignal.response/data resp))))))))

;; ---------------------------------------------------------------------------
;; Forecast coercion
;; ---------------------------------------------------------------------------

(deftest forecast-point-coercion
  (let [raw (first (:forecast sample-forecast-response))
        dp (entities/->forecast-point raw)]
    (testing "point-time is an Instant"
      (is (instance? java.time.Instant (:sgipsignal.forecast/point-time dp))))
    (testing "value preserved"
      (is (= 0.021803884178157724 (:sgipsignal.forecast/value dp))))
    (testing "raw metadata preserved"
      (is (= raw (:sgipsignal/raw (meta dp)))))))

(deftest forecast-response-coercion
  (let [resp (entities/->forecast-response sample-forecast-response)]
    (testing "data points coerced"
      (is (= 3 (count (:sgipsignal.response/data resp)))))
    (testing "generated-at is an Instant"
      (is (instance? java.time.Instant (:sgipsignal.response/generated-at resp))))
    (testing "tick intervals inferred from consecutive points (5 min)"
      (let [dp (first (:sgipsignal.response/data resp))]
        (is (= (:sgipsignal.forecast/point-time dp) (:tick/beginning dp)))
        (is (= (java.time.Instant/parse "2026-04-19T15:10:00Z") (:tick/end dp)))))))

;; ---------------------------------------------------------------------------
;; Long forecast coercion
;; ---------------------------------------------------------------------------

(deftest long-forecast-point-coercion
  (let [raw (first (:forecast sample-long-forecast-response))
        dp (entities/->long-forecast-point raw)]
    (testing "point-time is an Instant"
      (is (instance? java.time.Instant (:sgipsignal.long-forecast/point-time dp))))
    (testing "percentiles preserved"
      (is (= 0.03603787033073358 (:sgipsignal.long-forecast/percentile-15th dp)))
      (is (= 0.1628459292712714 (:sgipsignal.long-forecast/percentile-85th dp))))
    (testing "time-of-day preserved"
      (is (= "day" (:sgipsignal.long-forecast/time-of-day dp))))
    (testing "raw metadata preserved"
      (is (= raw (:sgipsignal/raw (meta dp)))))))

(deftest long-forecast-response-coercion
  (let [resp (entities/->long-forecast-response sample-long-forecast-response)]
    (testing "data points coerced"
      (is (= 2 (count (:sgipsignal.response/data resp)))))
    (testing "raw metadata preserved"
      (is (= sample-long-forecast-response (:sgipsignal/raw (meta resp)))))))

;; ---------------------------------------------------------------------------
;; Coerced schema validation
;; ---------------------------------------------------------------------------

(deftest coerced-schema-validation
  (testing "coerced MoerResponse validates (current)"
    (let [resp (entities/->moer-response sample-moer-current)]
      (is (nil? (m/explain schema/MoerResponse resp)))))

  (testing "coerced MoerResponse validates (historical)"
    (let [resp (entities/->moer-response sample-moer-historical)]
      (is (nil? (m/explain schema/MoerResponse resp)))))

  (testing "coerced ForecastResponse validates"
    (let [resp (entities/->forecast-response sample-forecast-response)]
      (is (nil? (m/explain schema/ForecastResponse resp)))))

  (testing "coerced LongForecastResponse validates"
    (let [resp (entities/->long-forecast-response sample-long-forecast-response)]
      (is (nil? (m/explain schema/LongForecastResponse resp))))))
