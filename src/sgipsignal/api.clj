(ns sgipsignal.api
  "Raw HTTP access to the SGIP Signal API.

  Stateless functions that take a config map and query params, returning
  raw hato HTTP responses. No coercion, no rate limiting, no auth management.

  Config map shape:
    {:base-url \"https://sgipsignal.com\"   ; optional, this is the default
     :token    \"jwt-token-string\"          ; required for data endpoints
     :username \"user\"                      ; required for login/register
     :password \"pass\"}                     ; required for login/register"
  (:require [hato.client :as hc])
  (:import [java.util Base64]))

;; ---------------------------------------------------------------------------
;; Defaults
;; ---------------------------------------------------------------------------

(def default-base-url "https://sgipsignal.com")

(def lib-version "0.1.0")

(def default-user-agent (str "clj-sgipsignal/" lib-version))

;; ---------------------------------------------------------------------------
;; Internal helpers
;; ---------------------------------------------------------------------------

(defn- base-url [cfg]
  (or (:base-url cfg) default-base-url))

(defn- bearer-headers [cfg]
  {"Authorization" (str "Bearer " (:token cfg))
   "User-Agent"    (or (:user-agent cfg) default-user-agent)})

(defn- kebab->snake [k]
  (-> (name k)
      (.replace "-" "_")))

(defn- snake-case-params
  "Convert a kebab-case keyword map to snake_case string keys,
  removing nil values."
  [params]
  (into {}
        (comp (filter (comp some? val))
              (map (fn [[k v]] [(kebab->snake k) v])))
        params))

;; ---------------------------------------------------------------------------
;; Auth endpoints (no token required)
;; ---------------------------------------------------------------------------

(defn- basic-auth-header
  "Encode username:password as a Basic auth header value."
  [username password]
  (let [creds (str username ":" password)]
    (str "Basic " (.encodeToString (Base64/getEncoder) (.getBytes creds "UTF-8")))))

(defn login
  "GET /login with HTTP Basic auth.
  Returns the full HTTP response. On success, body contains {:token \"...\"}."
  [cfg]
  (hc/get (str (base-url cfg) "/login")
          {:headers          {"User-Agent"    (or (:user-agent cfg) default-user-agent)
                              "Authorization" (basic-auth-header (:username cfg) (:password cfg))}
           :as               :json
           :throw-exceptions? false}))

(defn register
  "POST /register to create a new SGIP Signal account.
  params: {:username :password :email :org (optional)}"
  [cfg params]
  (hc/post (str (base-url cfg) "/register")
           {:headers          {"User-Agent" (or (:user-agent cfg) default-user-agent)}
            :content-type     :json
            :form-params      (snake-case-params params)
            :as               :json
            :throw-exceptions? false}))

(defn password-reset
  "GET /password to trigger a password reset email.
  params: {:username \"...\"}"
  [cfg params]
  (hc/get (str (base-url cfg) "/password")
          {:headers          {"User-Agent" (or (:user-agent cfg) default-user-agent)}
           :query-params     (snake-case-params params)
           :as               :json
           :throw-exceptions? false}))

;; ---------------------------------------------------------------------------
;; Data endpoints (token required)
;; ---------------------------------------------------------------------------

(defn moer
  "GET /sgipmoer — Real-time and historical MOER data.
  params: {:ba \"SGIP_CAISO_PGE\"           ; required
           :starttime \"2024-01-01T00:00Z\"  ; optional
           :endtime \"2024-01-02T00:00Z\"    ; optional
           :version \"1.0\"}                 ; optional"
  [cfg params]
  (hc/get (str (base-url cfg) "/sgipmoer")
          {:headers      (bearer-headers cfg)
           :query-params (snake-case-params params)
           :as           :json
           :throw-exceptions? false}))

(defn forecast
  "GET /sgipforecast — 72-hour forecast.
  params: {:ba \"SGIP_CAISO_PGE\"           ; required
           :starttime \"2024-01-01T00:00Z\"  ; optional
           :endtime \"2024-01-02T00:00Z\"    ; optional
           :version \"1.0\"}                 ; optional"
  [cfg params]
  (hc/get (str (base-url cfg) "/sgipforecast")
          {:headers      (bearer-headers cfg)
           :query-params (snake-case-params params)
           :as           :json
           :throw-exceptions? false}))

(defn long-forecast
  "GET /sgiplongforecast — Long-term forecast (month or year horizon).
  params: {:ba \"SGIP_CAISO_PGE\"           ; required
           :horizon \"month\"               ; required: \"month\" or \"year\"
           :starttime \"2024-01-01T00:00Z\"  ; optional
           :endtime \"2024-01-02T00:00Z\"    ; optional}"
  [cfg params]
  (hc/get (str (base-url cfg) "/sgiplongforecast")
          {:headers      (bearer-headers cfg)
           :query-params (snake-case-params params)
           :as           :json
           :throw-exceptions? false}))

;; ---------------------------------------------------------------------------
;; Response helpers
;; ---------------------------------------------------------------------------

(defn success?
  "True if the HTTP response has a 2xx status code."
  [response]
  (<= 200 (:status response) 299))

(defn body
  "Extract the :body from an HTTP response."
  [response]
  (:body response))
