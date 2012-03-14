(ns authproxy.login
  (:require [authproxy.httputil :as httputil])
  (:use ring.util.response)
  (:require [clojure.tools.logging :as log])
  (:require [clojure.java.io :as io]))

(def login-url (atom nil))
(def credentials (atom {}))
(defn user-password
  [username]
  (get @credentials username))

; TODO: what if the first request is a POST?
(defn auth-redirect
  "returns the redirect response for an unauthenticated user"
  [req]
  (log/debug "Redirecting to proxy login page with return target: " (httputil/request-url req))
  { :status 302
    :headers { "Location" (str @login-url "?destination=" (httputil/request-url req)) }})

(defn proxy-login
  "Login page submits to this function"
  [req]
  (let [username (get (:form-params req) "username")
        password (get (:form-params req) "password")
        target (get (:form-params req) "destination")]
    (log/debug "Logging in user:" username " and directing to" target)
    ;(log/debug "Request: " req)
    ;(log/debug "Session: " (:session req))
    ;TODO: validate credentials before storing - LDAPS bind to AD
    (swap! credentials assoc username password)
    { :status 302
      :session (assoc (:session req) "proxy-user" username)
      :headers { "Location" target } }))

(defn proxy-logout
  "Logs the current user out of the proxy so subsequent auto-authentication won't happen"
  [{session :session}]
  (let [username (get session "proxy-user")]
    (swap! credentials dissoc username)
    { :status 200
      :session (dissoc session "proxy-user")
      :body (io/input-stream (io/resource "public/pxylogoutconfirm.html"))}))


(defn- apply-destination
  "returns an input stream to the generic logic form with the destination applied"
  [req]
  (println req)
  (let [raw (slurp (io/resource "public/pxyform.html"))]
    (java.io.BufferedInputStream. (java.io.ByteArrayInputStream. (.getBytes (.replace raw "X-DESTINATION-X" (get (:params req) "destination")))))))

(defn proxy-form
  "Returns the login page"
  [req]
  { :status 200
    :headers { "Content-Type" "text/html" }
    :body (apply-destination req) })

