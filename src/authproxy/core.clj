(ns authproxy.core
  (:use ring.adapter.jetty)
  (:use ring.middleware.resource)
  (:use ring.middleware.session)
  (:use ring.middleware.keyword-params)
  (:use ring.middleware.params)
  (:use authproxy.httputil)
  (:require [authproxy.login :as login])
  (:require [clojure.tools.logging :as log])
  (:import [java.net URL URLConnection HttpURLConnection]))


; TODO: may want to have host values here as well - 
; TODO: probably want a setting to shut off automatic auth for certain hosts
(def mapping { "rfc.thinkerjk.com:8081" "http://www.ietf.org" 
               "nyt.thinkerjk.com:8081" "http://www.nytimes.com"
               "tcmanager.thinkerjk.com:8081" "http://localhost:8080"}) 

; Vars (threadlocals) to be bound to username and password for a specific request
(def ^:dynamic *username* nil)
(def ^:dynamic *password* nil)

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
  "Issues a new http request to the specified target based on the specified request - this is the actual proxying"
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

(defn proxy-handler 
  "Ring handler for proxying requests - determines which host to map to and issues an http request to that host"
  [req]
  (log/trace "Received request: " req)
  (let [uname (get (:session req) "proxy-user")] ; get username from session
    (if (nil? uname) ; if we don't have one, redirect to the authentication page
        (login/auth-redirect req)
        (binding [*username* uname
                  *password* (login/user-password uname)]
          (log/debug "Credentials initialized: " *username*)
          (let [target (target-url req)
                resp (issue-request req target)]
            (proxy-response req resp))))))

(defn- router 
  "Entry point for requests - looks at the URI to determine how the request should be processed."
  [req]
  (log/debug "XXXXXXXXXXXXXXX Routing URI " (:uri req))
  (log/debug "Routing request:" req)
  (condp = (:uri req)
    "/favicon.ico" { :status 404 } ; TODO: remove this when session thing is fixed
    "/pxylogin" (login/proxy-login req)
    "/pxyform" (login/proxy-form req)
    (proxy-handler req)))

(def app-chain
  (-> router
    (wrap-session)
    (wrap-params)
    (wrap-keyword-params)
    ))

(defn -main [& args]
  (register-authenticator)
  ; TODO: take port number as an argument
  (run-jetty app-chain {:port 8081} ))
