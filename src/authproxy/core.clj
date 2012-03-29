(ns authproxy.core
  (:use ring.adapter.jetty)
  (:use ring.middleware.resource)
  (:use ring.middleware.session)
  (:use ring.middleware.keyword-params)
  (:use ring.middleware.params)
  (:use authproxy.httputil)
  (:require [authproxy.login :as login])
  (:require [clojure.tools.logging :as log])
  (:import [java.net URL URLConnection HttpURLConnection])
  (:import [org.apache.commons.io IOUtils])
  (:gen-class))


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
        (log/trace "Creating password authentication for " *username*)
        (java.net.PasswordAuthentication. *username* (.toCharArray *password*))))))

(defn- return-stream
  "Handles exceptions thrown when there is a non-successful response code and switches to the 
  error stream instead"
  [connection]
  (try
    (.getInputStream connection)
    (catch java.lang.Exception ioe
      (.getErrorStream connection))))

; file uploads, multi-part stuff will break this
(defn- write-body!
  "For post/put requests, copies the contents from the request's input stream to the output stream for the URLconnection"
  [req urlconn]
  (IOUtils/copy (:body req) (.getOutputStream urlconn)))


(defn copy-header?
  "returns true if the specified Map.Entry for an HTTP header should be migrated to the response of the client"
  [header-entry]
  (cond 
    (nil? (.getKey header-entry)) false ;remove the HTTP 1.1/200 OK line 
    (and (= (.getKey header-entry) "Transfer-Encoding") (= (.getValue header-entry) "chunked")) false
    :else true))

(defn convert-header-map
  [headers]
  (reduce
    #(assoc %1 (.getKey %2) (header-list-to-string (.getValue %2)))
    {}
    (filter copy-header? headers)))

(defn- issue-request
  "Issues a new http request to the appropriate target based on the specified request - this is the actual proxying"
  [req]
  (let [url (URL. (request-url req))
        conn (.openConnection url)]
    (.setInstanceFollowRedirects conn false)
    (.setRequestMethod conn (http-method req))
    (doseq [h (:headers req)]
      (.addRequestProperty conn (first h) (second h)))
    (when (= :post (:request-method req)) ; TODO: add put as well
      (log/debug "Found post request - writing body")
      (.setDoOutput conn true)
      (write-body! req conn))
    (.connect conn)
    { :status (.getResponseCode conn)
      :headers (convert-header-map (.getHeaderFields conn))
      :body (return-stream conn) }))

(defn- proxy-handler 
  "Ring handler for proxying requests - determines which host to map to and issues an http request to that host"
  [req]
  (log/trace "Received request: " req)
  (let [uname (get (:session req) "proxy-user")
        pwd (get (:session req) "proxy-pwd")] ; get username from session
    (if (or (nil? uname) (nil? pwd)) ; if we don't have one, redirect to the authentication page
        (login/auth-redirect req)
        (binding [*username* uname
                  *password* pwd]
          (log/debug "Credentials initialized: " *username*)
          (issue-request req)))))

(defn- router 
  "Entry point for requests - looks at the URI to determine how the request should be processed."
  [req]
  (log/debug "Routing URI " (get (:headers req) "host") (:uri req))
  (condp = (:uri req)
    "/pxylogin" ((wrap-params login/proxy-login) req)
    "/pxylogout" ((wrap-params login/proxy-logout) req)
    "/pxyform" ((wrap-params login/proxy-form) req)
    (proxy-handler req))) ; Don't want to wrap params here as we don't want to parse the body - it will be forwarded along as is to the proxy instead

(defn- configure-jetty
  "Callback to configure the jetty server"
  [server]
  nil)

(defn- build-app-chain
  [domain]
    ; can't wrap-params because it will read the input streams of forms - want to leave the body unparsed for proxied requests
      (wrap-session router {:cookie-attrs { :domain domain :path "/" }}))

(defn -main [& args]
  (if (< (count args) 3) 
      (println "Usage: port domain loginurl")
      (let [[port domain loginurl] args]
        ; TODO: is this the best way to pass this configuration
        (compare-and-set! authproxy.login/login-url nil loginurl)
        (register-authenticator)
        (run-jetty 
          (build-app-chain domain)
          {:port (Integer/parseInt port)
          ; :configurator configure-jetty
           } ))))
