(ns authproxy.core
    (:use ring.adapter.jetty)
    (:require [clj-http.client :as client])
    ;(:import [org.mortbay.jetty Server])
    ;(:import [org.mortbay.jetty.servlet Context ServletHolder])
    ;(:import [javax.servlet Servlet])
    )

(def mapping { "jkrfc:8080" "http://www.ietf.org" }) 
              

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

(defn- query-string
  [req]
  (if (nil? (:query-string req)) ""
    (str "?" (:query-string req))))

(defn- target-url
  "Returns the appropriate target url from the mapping"
  [req]
  (str (get mapping (host-value req)) (:uri req) (query-string req)))

(defn handler [req]
  (println req)
  ;{ :status 200 })
  (let [target (target-url req)
        resp (client/get target (dissoc (:headers req) "host"))]
    (println "Routing to target " target)
    (if (= (:status resp) 401) "Auth challenge detected" "No auth, passing to client")
    resp))
   

(defn -main [& args]
  (run-jetty #'handler {:port 8080} ))
