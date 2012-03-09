(ns authproxy.core
    (:use ring.adapter.jetty)
    (:require [clj-http.client :as client])
  (:import [java.net URL URLConnection HttpURLConnection])
    ;(:import [org.mortbay.jetty Server])
    ;(:import [org.mortbay.jetty.servlet Context ServletHolder])
    ;(:import [javax.servlet Servlet])
    )

; TODO: may want to have host values here as well
(def mapping { "rfc.thinkerjk.com:8081" "http://www.ietf.org" 
               "nyt.thinkerjk.com:8081" "http://www.nytimes.com"
               "tcmanager.thinkerjk.com:8081" "http://localhost:8080"}) 
              

(comment
(defn proxy-servlet
    "constructs and returns the servlet that proxies requests"
    []
    (proxy [javax.servlet.http.HttpServlet] []
        (doGet [req resp]
            (.println (.getOutputStream resp) "<html><body><p>Hello World</p></body></html>"))))

(defn -main [& args]
  (let [server (Server. 8080)
        ctx (Context. server "/" Context/SESSIONS)]
    (.addServlet ctx (ServletHolder. (proxy-servlet)) "/*")
    (.start server)))
)
;---------------------------------------------------------------------------------------------

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
  (str (get mapping (host-value req)) (:uri req) (query-string req)))

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
      (println headers)
      { :status (.getResponseCode conn)
        :headers headers
        :body (return-stream conn) })))

(defn handler [req]
  (println req)
  ;{ :status 200 })
  (let [target (target-url req)
        resp (issue-request req target)]
    ; TODO: switch to log4j and add appropriate tracing
    (println "Routing to target " target)
    (println (if (= (:status resp) 401) "Auth challenge detected" "No auth, passing to client"))
    resp))
   

(defn -main [& args]
  (run-jetty #'handler {:port 8081} ))
