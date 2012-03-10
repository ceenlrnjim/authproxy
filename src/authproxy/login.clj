(ns authproxy.login
  (:require [authproxy.httputil :as httputil])
  (:use ring.util.response)
  (:require [clojure.tools.logging :as log])
  (:require [clojure.java.io :as io]))

; TODO: change this to come from command line
(def login-url "http://localhost:8081/pxyform")

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
    :headers { "Location" (str login-url "?destination=" (httputil/request-url req)) }})

(defn proxy-login
  "Login page submits to this function"
  [req]
  (let [username (get (:form-params req) "username")
        password (get (:form-params req) "password")
        target (get (:form-params req) "destination")]
    (log/debug "Logging in user:" username " and directing to" target)
    (log/debug "Request: " req)
    (log/debug "Session: " (:session req))
    (swap! credentials assoc username password)
    { :status 302
      :session (assoc (:session req) "proxy-user" username)
      :headers { "Location" target } }))

(defn- apply-destination
  "returns an input stream to the generic logic form with the destination applied"
  [req]
  (let [raw (slurp (io/resource "public/pxyform.html"))]
    (java.io.BufferedInputStream. (java.io.ByteArrayInputStream. (.getBytes (.replace raw "X-DESTINATION-X" (get (:params req) "destination")))))))

(defn proxy-form
  "Returns the login page"
  [req]
  { :status 200
    :headers { "Content-Type" "text/html" }
    :body (apply-destination req) })

