(ns sgipsignal.integration-test
  "Live integration tests against the SGIP Signal API.

  Requires SGIP_USER and SGIP_PASSWORD environment variables.
  Run with: clojure -M:test-integration

  These tests make real HTTP calls and validate the full pipeline:
  raw response -> schema validation -> entity coercion -> coerced schema validation."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [malli.core :as m]
            [sgipsignal.api :as api]
            [sgipsignal.client :as client]
            [sgipsignal.entities :as entities]
            [sgipsignal.entities.schema :as schema]
            [sgipsignal.entities.schema.raw :as raw])
  (:import [java.time ZoneId ZonedDateTime]))

;; ---------------------------------------------------------------------------
;; Fixture: skip if credentials not available
;; ---------------------------------------------------------------------------

(def ^:private credentials-available?
  (and (System/getenv "SGIP_USER")
       (System/getenv "SGIP_PASSWORD")))

(defn- require-credentials [f]
  (if credentials-available?
    (f)
    (println "SKIPPING integration tests: SGIP_USER/SGIP_PASSWORD not set")))

(use-fixtures :once require-credentials)

;; Shared client — created once per test run
(def ^:private test-client
  (delay
    (client/make-client)))

;; ---------------------------------------------------------------------------
;; Login
;; ---------------------------------------------------------------------------

(deftest login-test
  (testing "login returns a valid token"
    (let [resp (api/login {:username (System/getenv "SGIP_USER")
                           :password (System/getenv "SGIP_PASSWORD")})]
      (is (api/success? resp))
      (is (string? (get-in resp [:body :token])))
      (is (nil? (m/explain raw/LoginResponse (:body resp)))))))

;; ---------------------------------------------------------------------------
;; MOER — current (single object)
;; ---------------------------------------------------------------------------

(deftest moer-current-test
  (testing "/sgipmoer returns current MOER as single object"
    (let [resp (client/moer @test-client {:ba "SGIP_CAISO_PGE"})]
      (is (api/success? resp))
      (let [body (api/body resp)]
        (testing "raw response validates against schema"
          (is (nil? (m/explain raw/MoerPoint body))))
        (testing "coerces to valid entity"
          (let [coerced (entities/->moer-response body)]
            (is (= 1 (count (:sgipsignal.response/data coerced))))
            (is (nil? (m/explain schema/MoerResponse coerced)))
            (let [dp (first (:sgipsignal.response/data coerced))]
              (is (instance? ZonedDateTime (:sgipsignal.moer/point-time dp)))
              (is (number? (:sgipsignal.moer/value dp)))
              (is (= "SGIP_CAISO_PGE" (:sgipsignal.moer/ba dp)))
              (is (some? (:tick/beginning dp)))
              (is (some? (:tick/end dp))))))))))

;; ---------------------------------------------------------------------------
;; MOER — historical (array)
;; ---------------------------------------------------------------------------

(deftest moer-historical-test
  (testing "/sgipmoer with time range returns array"
    (let [now (java.time.Instant/now)
          one-hour-ago (.minus now (java.time.Duration/ofHours 1))
          resp (client/moer @test-client
                            {:ba "SGIP_CAISO_PGE"
                             :starttime (str one-hour-ago)
                             :endtime (str now)})]
      (is (api/success? resp))
      (let [body (api/body resp)]
        (testing "raw response is an array"
          (is (sequential? body))
          (is (pos? (count body)))
          (is (nil? (m/explain raw/MoerHistoricalResponse body))))
        (testing "coerces to valid entity with multiple points"
          (let [coerced (entities/->moer-response body)]
            (is (< 1 (count (:sgipsignal.response/data coerced))))
            (is (nil? (m/explain schema/MoerResponse coerced)))))))))

;; ---------------------------------------------------------------------------
;; MOER* — coerced convenience function
;; ---------------------------------------------------------------------------

(deftest moer-star-test
  (testing "moer* returns coerced entity directly"
    (let [result (client/moer* @test-client {:ba "SGIP_CAISO_PGE"})]
      (is (some? result))
      (is (nil? (m/explain schema/MoerResponse result)))
      (is (some? (:sgipsignal/raw (meta result)))))))

;; ---------------------------------------------------------------------------
;; Forecast
;; ---------------------------------------------------------------------------

(deftest forecast-test
  (testing "/sgipforecast returns forecast with generated_at"
    (let [resp (client/forecast @test-client {:ba "SGIP_CAISO_PGE"})]
      (is (api/success? resp))
      (let [body (api/body resp)]
        (testing "raw response validates"
          (is (nil? (m/explain raw/ForecastResponse body))))
        (testing "coerces to valid entity"
          (let [coerced (entities/->forecast-response body)]
            (is (instance? ZonedDateTime (:sgipsignal.response/generated-at coerced)))
            (is (pos? (count (:sgipsignal.response/data coerced))))
            (is (nil? (m/explain schema/ForecastResponse coerced)))
            (let [dp (first (:sgipsignal.response/data coerced))]
              (is (instance? ZonedDateTime (:sgipsignal.forecast/point-time dp)))
              (is (number? (:sgipsignal.forecast/value dp)))
              (is (some? (:tick/beginning dp)))
              (is (some? (:tick/end dp))))))))))

;; ---------------------------------------------------------------------------
;; Forecast* — coerced convenience function
;; ---------------------------------------------------------------------------

(deftest forecast-star-test
  (testing "forecast* returns coerced entity directly"
    (let [result (client/forecast* @test-client {:ba "SGIP_CAISO_PGE"})]
      (is (some? result))
      (is (nil? (m/explain schema/ForecastResponse result)))
      (is (some? (:sgipsignal/raw (meta result)))))))

;; ---------------------------------------------------------------------------
;; Long forecast
;; ---------------------------------------------------------------------------

(deftest long-forecast-test
  (testing "/sgiplongforecast returns percentile bands"
    (let [resp (client/long-forecast @test-client
                                     {:ba "SGIP_CAISO_PGE" :horizon "month"})]
      (is (api/success? resp))
      (let [body (api/body resp)]
        (testing "raw response validates"
          (is (nil? (m/explain raw/LongForecastResponse body))))
        (testing "coerces to valid entity"
          (let [coerced (entities/->long-forecast-response body)]
            (is (pos? (count (:sgipsignal.response/data coerced))))
            (is (nil? (m/explain schema/LongForecastResponse coerced)))
            (let [dp (first (:sgipsignal.response/data coerced))]
              (is (instance? ZonedDateTime (:sgipsignal.long-forecast/point-time dp)))
              (is (number? (:sgipsignal.long-forecast/percentile-15th dp)))
              (is (number? (:sgipsignal.long-forecast/percentile-85th dp)))
              (is (string? (:sgipsignal.long-forecast/time-of-day dp)))
              (is (#{"morning" "day" "evening" "night"}
                   (:sgipsignal.long-forecast/time-of-day dp))))))))))

;; ---------------------------------------------------------------------------
;; Long forecast* — coerced convenience function
;; ---------------------------------------------------------------------------

(deftest long-forecast-star-test
  (testing "long-forecast* returns coerced entity directly"
    (let [result (client/long-forecast* @test-client
                                        {:ba "SGIP_CAISO_PGE" :horizon "month"})]
      (is (some? result))
      (is (nil? (m/explain schema/LongForecastResponse result)))
      (is (some? (:sgipsignal/raw (meta result)))))))

;; ---------------------------------------------------------------------------
;; Multiple regions
;; ---------------------------------------------------------------------------

(deftest multiple-regions-test
  (testing "MOER works for non-CAISO regions"
    (let [result (client/moer* @test-client {:ba "SGIP_CAISO_SCE"})]
      (is (some? result))
      (is (= "SGIP_CAISO_SCE"
             (:sgipsignal.moer/ba (first (:sgipsignal.response/data result))))))))

;; ---------------------------------------------------------------------------
;; Per-instance :zone — non-UTC presentation
;; ---------------------------------------------------------------------------

(deftest zoned-client-test
  (testing "client constructed with :zone returns ZonedDateTimes in that zone"
    (let [la-zone (ZoneId/of "America/Los_Angeles")
          la-client (client/make-client {:zone la-zone})
          result (client/moer* la-client {:ba "SGIP_CAISO_PGE"})
          dp (first (:sgipsignal.response/data result))]
      (is (instance? ZonedDateTime (:sgipsignal.moer/point-time dp)))
      (is (= la-zone (.getZone ^ZonedDateTime (:sgipsignal.moer/point-time dp))))
      (is (= la-zone (.getZone ^ZonedDateTime (:tick/beginning dp))))
      (is (= la-zone (.getZone ^ZonedDateTime (:tick/end dp)))))))
