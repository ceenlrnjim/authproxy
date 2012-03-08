(ns authproxy.core
    (:use ring.adapter.jetty))

(def mapping { "source" "http://someotherhost:port/target" })

(defn handler [req]
  { :status 200
    :headers {"content-type" "text/html"}
    :body "Hello World"})

(defn -main [& args]
  (run-jetty #'handler {:port 8081} ))
