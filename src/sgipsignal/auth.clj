(ns sgipsignal.auth
  "SGIP Signal JWT token management with automatic refresh.

  The SGIP Signal API uses HTTP Basic auth to obtain a JWT token
  (GET /login), which is valid for 30 minutes. This namespace
  manages token lifecycle with automatic refresh."
  (:require [sgipsignal.api :as api])
  (:import [java.time Instant Duration]))

(def ^:private refresh-before
  "Refresh the token when less than this duration remains."
  (Duration/ofMinutes 5))

(def ^:private token-lifetime
  "JWT token lifetime (30 minutes per SGIP Signal docs)."
  (Duration/ofMinutes 30))

(defn create-auth
  "Create an auth manager for automatic token lifecycle.

  Options:
    :username  — SGIP Signal username (or env SGIP_USER)
    :password  — SGIP Signal password (or env SGIP_PASSWORD)
    :base-url  — API base URL (default https://sgipsignal.com)

  Returns an auth map with an atom-backed token state."
  [{:keys [username password base-url]}]
  (let [username (or username (System/getenv "SGIP_USER"))
        password (or password (System/getenv "SGIP_PASSWORD"))]
    (when-not (and username password)
      (throw (ex-info "SGIP Signal credentials required. Provide :username/:password or set SGIP_USER/SGIP_PASSWORD env vars."
                      {:username (some? username) :password (some? password)})))
    {:username  username
     :password  password
     :base-url  (or base-url api/default-base-url)
     :state     (atom {:token nil :expires-at nil})}))

(defn- token-valid?
  "True if the stored token has enough remaining lifetime."
  [{:keys [token expires-at]}]
  (and token expires-at
       (.isAfter ^Instant expires-at
                 (.plus (Instant/now) refresh-before))))

(defn- refresh-token!
  "Login and update the token state. Returns the new token string."
  [{:keys [username password base-url state] :as _auth}]
  (let [resp (api/login {:username username :password password :base-url base-url})]
    (if (api/success? resp)
      (let [new-token (get-in resp [:body :token])
            expires   (.plus (Instant/now) token-lifetime)]
        (reset! state {:token new-token :expires-at expires})
        new-token)
      (throw (ex-info "SGIP Signal login failed"
                      {:status (:status resp) :body (:body resp)})))))

(defn token
  "Get a valid JWT token, refreshing if needed. Thread-safe."
  [{:keys [state] :as auth}]
  (let [current @state]
    (if (token-valid? current)
      (:token current)
      (locking state
        ;; Double-check after acquiring lock
        (let [current @state]
          (if (token-valid? current)
            (:token current)
            (refresh-token! auth)))))))
