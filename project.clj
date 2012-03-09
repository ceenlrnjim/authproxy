(defproject authproxy "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 ;[org.mortbay.jetty/jetty-embedded "6.1.26"]
                 [ring/ring-core "1.0.2"]
                 [ring/ring-jetty-adapter "1.0.2"]
                 [clj-http "0.1.3"]]
  :main authproxy.core
  )
