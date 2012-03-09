(ns authproxy.core
  (:use ring.adapter.jetty)
  (:require [clojure.tools.logging :as log])
  ;(:require [clj-http.client :as client])
  (:import [java.net URL URLConnection HttpURLConnection]))

; TODO: may want to have host values here as well - 
; TODO: probably want a setting to shut off automatic auth for certain hosts
(def mapping { "rfc.thinkerjk.com:8081" "http://www.ietf.org" 
               "nyt.thinkerjk.com:8081" "http://www.nytimes.com"
               "tcmanager.thinkerjk.com:8081" "http://localhost:8080"}) 

; Vars (threadlocals) to be bound to username and *password* for a specific request
(def ^:dynamic *username* nil)
(def ^:dynamic *password* nil)

; Credential store for all logged in users
; TODO: ultimately want to capture user credentials and save to their session
(def credentials (atom {"tomcat" "tomcat"}))

(defn- register-authenticator
  "Registers a default authenticator that pulls username and password from the thread local
  bindings set up when the request is initiated."
  []
  (java.net.Authenticator/setDefault
    (proxy [java.net.Authenticator] []
      (getPasswordAuthentication []
        (log/debug "Creating password authentication for " *username* "/" *password*)
        (java.net.PasswordAuthentication. *username* (.toCharArray *password*))))))

(defn- host-value
  "Returns the host header value from the specified request"
  [req]
  (get (:headers req) "host"))

(defn- query-string
  [req]
  (if (nil? (:query-string req)) ""
    (str "?" (:query-string req))))

(defn- target-url
  "Returns the appropriate target url from the mapping"
  [req]
  (let [host (host-value req)
        target (str (get mapping (host-value req)) (:uri req) (query-string req))]
    (log/debug "Generating target " target " for host " host)
    target))

(defn- header-list-to-string
  [value-list]
  (reduce 
    #(str %1 (if (= %1 "") "" ", ") %2)
    ""
    value-list))

(defn- convert-header-map
  [headers]
  (dissoc 
    (reduce
      #(assoc %1 (.getKey %2) (header-list-to-string (.getValue %2)))
      {}
      headers)
    nil)) ; remove the HTTP 1.1/200 OK line 

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

; TODO: support SPNEGO, NTLM, etc,
; Starting with just Basic auth
; TODO: currently not doing anything - seeing if the java authenticator will do everything I need
; TODO: do I need to do this or will the java.net.authenticator take care of everything?
;(defn- proxy-auth
;  "Handles an authentication request returned by a proxied web location.
;  req is the original request to the proxy
;  resp is the response generated from the proxied web site"
;  [req resp]
;  (log/debug "Handling auth request...")
;  resp)

(defn- proxy-response
  "Provide any logic to handle any responses that require logic, otherwise just return back to client"
  [req resp]
  resp)
  ;(cond (= (:status resp) 401) (proxy-auth req resp)
  ;      :else resp))

(defn handler [req]
  (log/debug "Received request: " req)
  ;{ :status 200 })
  ; TODO: look at session, get user identifier and then use that to look up credentials in the credentials atom
  (binding [*username* "tomcat"
            *password* (get @credentials "tomcat")]
    (log/debug "Credentials initialized: " *username* "/" *password*)
    (let [target (target-url req)
          resp (issue-request req target)]
      (proxy-response req resp))))
   

(defn -main [& args]
  (register-authenticator)
  (run-jetty #'handler {:port 8081} ))
