(ns authproxy.core
  (:use ring.adapter.jetty)
  (:use ring.middleware.resource)
  (:use ring.middleware.session)
  (:use ring.middleware.keyword-params)
  (:use ring.middleware.params)
  (:use authproxy.httputil)
  (:require [clojure.tools.logging :as log])
  (:require [clojure.java.io :as io])
  (:import [java.net URL URLConnection HttpURLConnection]))


; TODO: may want to have host values here as well - 
; TODO: probably want a setting to shut off automatic auth for certain hosts
(def mapping { "rfc.thinkerjk.com:8081" "http://www.ietf.org" 
               "nyt.thinkerjk.com:8081" "http://www.nytimes.com"
               "tcmanager.thinkerjk.com:8081" "http://localhost:8080"}) 

(def login-url "http://localhost:8081/pxyform")

; Vars (threadlocals) to be bound to username and *password* for a specific request
(def ^:dynamic *username* nil)
(def ^:dynamic *password* nil)

(def credentials (atom {}))

(defn- register-authenticator
  "Registers a default authenticator that pulls username and password from the thread local
  bindings set up when the request is initiated."
  []
  (java.net.Authenticator/setDefault
    (proxy [java.net.Authenticator] []
      (getPasswordAuthentication []
        (log/debug "Creating password authentication for " *username* "/" *password*)
        (java.net.PasswordAuthentication. *username* (.toCharArray *password*))))))


(defn- target-url
  "Returns the appropriate target url from the mapping"
  [req]
  (let [host (host-value req)
        target (str (get mapping (host-value req)) (:uri req) (query-string req))]
    (log/debug "Generating target " target " for host " host)
    target))


(defn- return-stream
  "Handles exceptions thrown when there is a non-successful response code and switches to the 
  error stream instead"
  [connection]
  (try
    (.getInputStream connection)
    (catch java.lang.Exception ioe
      (.getErrorStream connection))))

; TODO: support for posts
(defn- issue-request
  [req target]
  (let [url (URL. target)
        conn (.openConnection url)]
    (doseq [h (dissoc (:headers req) "host")]
      (.addRequestProperty conn (first h) (second h)))
    ;(.setDoOutput conn true)
    (.connect conn)
    (let [headers (convert-header-map (.getHeaderFields conn))]
      { :status (.getResponseCode conn)
        :headers headers
        :body (return-stream conn) })))

(defn- proxy-response
  "Provide any logic to handle any responses that require logic, otherwise just return back to client"
  [req resp]
  resp)
  ;(cond (= (:status resp) 401) (proxy-auth req resp)
  ;      :else resp))


; TODO: what if the first request is a POST?
(defn- auth-redirect
  "returns the redirect response for an unauthenticated user"
  [req]
  (log/debug "Redirecting to proxy login page with return target: " (request-url req))
  { :status 302
    :session (assoc (:session req) "originalTarget" (request-url req))
    :headers { "Location" login-url }})

(defn proxy-handler [req]
  (log/debug "Received request: " req)
  (let [uname (get (:session req) "proxy-user")] ; get username from session
    (if (nil? uname) ; if we don't have one, redirect to the authentication page
        (auth-redirect req)
        (binding [*username* uname
                  *password* (get @credentials uname)]
          (log/debug "Credentials initialized: " *username* "/" *password*)
          (let [target (target-url req)
                resp (issue-request req target)]
            (proxy-response req resp))))))

(defn- proxy-login
  "Login page submits to this function"
  [req]
  ; TODO: add validation of credentials
  (let [username (get (:form-params req) "username")
        password (get (:form-params req) "password")
        target (get (:session req) "originalTarget")]
    (log/debug "Logging in user:" username " and directing to" target)
    (log/debug "Request: " req)
    (log/debug "Session: " (:session req))
    (swap! credentials assoc username password)
    { :status 302
      :session (assoc (:session req) "proxy-user" username)
      :headers { "Location" target } }))

(defn- proxy-form
  "Returns the login page"
  [req]
  { :status 200
    :headers { "Content-Type" "text/html" }
    :body (io/input-stream (io/resource "public/pxyform.html"))})

(defn- router [req]
  ;(log/debug "Routing request:" req)
  (condp = (:uri req)
    "/favicon.ico" { :status 404 }
    "/pxylogin" (proxy-login req)
    "/pxyform" (proxy-form req)
    (proxy-handler req)))

(def app-chain
  (-> router
    (wrap-session)
    (wrap-params)
    (wrap-keyword-params)
    ;(wrap-resource "public")
    ))
   

(defn -main [& args]
  (register-authenticator)
  (run-jetty app-chain {:port 8081} ))
