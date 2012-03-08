(ns authproxy.core
    ;(:use ring.adapter.jetty)
    (:import [org.mortbay.jetty Server])
    (:import [org.mortbay.jetty.servlet Context ServletHolder])
    (:import [javax.servlet Servlet])
    )

(def mapping { "source" "http://someotherhost:port/target" })

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

(comment
(defn handler [req]
  (println (.getClass req))
  (println req)
  { :status 200
    :headers {"content-type" "text/html"}
    :body "Hello World"})

(defn -main [& args]
  (run-jetty #'handler {:port 8080} ))
)
