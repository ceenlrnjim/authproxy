(ns authproxy.core
    (:use ring.adapter.jetty)
    (:require [clj-http.client :as client])
    ;(:import [org.mortbay.jetty Server])
    ;(:import [org.mortbay.jetty.servlet Context ServletHolder])
    ;(:import [javax.servlet Servlet])
    )

(def mapping { "localhost:8080" { "/a/b" "http://www.ietf.org" 
                                  "/nyt" "http://www.nytimes.com"} } ) 
              

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

(defn- host-value
  "Returns the host header value from the specified request"
  [req]
  (get (:headers req) "host"))

(defn- uri-target
  "Returns the first value from the map where the key matches the beginning of the uri for the specified request"
  [host-map req]
  (first (filter #(.startsWith (:uri req) (get % 0)) host-map)))

(defn- target-url
  "Returns the appropriate target url from the mapping"
  [req]
  (let [host-map (get mapping (host-value req))
        target (uri-target host-map req)]
    (str (get target 1) ; the host name from the nested map
         (.substring (:uri req) (.length (get target 0)))))) ; strip the uri key off the target url

(defn handler [req]
  (println req)
  ;{ :status 200 })
  (let [target (target-url req)]
    (println "Target " target)
   (client/get target (dissoc (:headers req) "host"))))

(defn -main [& args]
  (run-jetty #'handler {:port 8080} ))
