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
  (:gen-class))


; TODO: probably want a setting to shut off automatic auth for certain hosts

; redirect mapping for local development
(def mapping { "rfc.thinkerjk.com:8081" "http://www.ietf.org" 
               "nyt.thinkerjk.com:8081" "http://www.nytimes.com"
               "testapp.thinkerjk.com:8081" "http://localhost:8080"
               "tcmanager.thinkerjk.com:8081" "http://localhost:8080"}) 

(defn- lookup-mapper
  "Returns the url to proxy a request to based on the mapping above.
  This is required where you're running the proxy and the service on the same host
  (like in development) and have to modify hosts to have multiple names to this host"
  [req]
  (str (get mapping (host-value req)) (:uri req) (query-string req)))

(defn- passthrough-mapper
  "Mapper that just directs to the content of the host header"
  [req]
  (request-url req))

; TODO: need a mapper that leaves server names but handles port differences

; Switch definition when not running proxy on same host as target sites
;(def mapper lookup-mapper)
(def mapper passthrough-mapper)


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
  [req urlconn]
  (let [osw (java.io.OutputStreamWriter. (.getOutputStream urlconn))
        ^String body (reduce 
                      #(str 
                         %1
                         (if (= "" %1) "" "&")
                         ; TODO: get encoding from the request?
                         (java.net.URLEncoder/encode (first %2) "UTF-8") 
                         "=" 
                         ; TODO: get encoding from the request?
                         (java.net.URLEncoder/encode (second %2) "UTF-8")) 
                       "" 
                       (:form-params req))]
    (log/debug "write-body!> Params" (:form-params req) "yield form body" body)
    (.write osw body)
    (.flush osw)))

(defn- issue-request
  "Issues a new http request to the appropriate target based on the specified request - this is the actual proxying"
  [req]
  (let [url (URL. (mapper req))
        conn (.openConnection url)]
    (.setInstanceFollowRedirects conn false)
    (.setRequestMethod conn (http-method req))
    (doseq [h (dissoc (:headers req) "host")] ; TODO: do I want to remove host when not running in development
      (.addRequestProperty conn (first h) (second h)))
    (when (= :post (:request-method req)) ; TODO: add put as well
      (log/debug "Found post request - writing body")
      (.setDoOutput conn true)
      (write-body! req conn))
    (.connect conn)
    { :status (.getResponseCode conn)
      :headers (convert-header-map (.getHeaderFields conn))
      :body (return-stream conn) }))

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
          (proxy-response req (issue-request req))))))

(defn- router 
  "Entry point for requests - looks at the URI to determine how the request should be processed."
  [req]
  (log/debug "Routing URI " (get (:headers req) "host") (:uri req) "for session" (:session req))
  (condp = (:uri req)
    "/pxylogin" (login/proxy-login req)
    "/pxylogout" (login/proxy-logout req)
    "/pxyform" (login/proxy-form req)
    (proxy-handler req)))

(defn build-app-chain
  [domain]
  (wrap-keyword-params 
    (wrap-params 
      (wrap-session router {:cookie-attrs { :domain domain :path "/" }}))))

(defn -main [& args]
  (if (< (count args) 3) 
      (println "Usage: port domain loginurl")
      (let [[port domain loginurl] args]
        (System/setProperty "proxyLoginUrl" loginurl) ; TODO: must be a better way - can't using var bindings because it gets read in a different thread
        (register-authenticator)
        (run-jetty 
          (build-app-chain domain)
          {:port (Integer/parseInt port)} ))))
