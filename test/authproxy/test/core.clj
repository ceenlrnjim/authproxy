(ns authproxy.test.core
  (:use [authproxy.core])
  (:import [java.net Authenticator InetAddress])
  (:use [clojure.test]))

(def register-authenticator (ns-resolve 'authproxy.core 'register-authenticator))
(deftest test-register-authenticator
  (register-authenticator)
  (binding [*username* "myuser"
            *password* "mypass"]
    (let [passauth (Authenticator/requestPasswordAuthentication
                      "localhost"
                      (InetAddress/getByName "localhost")
                      80
                      "http"
                      "password?"
                      "basic")]
      (is (= "myuser" (.getUserName passauth)))
      (is (java.util.Arrays/equals (.getPassword passauth) (.toCharArray "mypass"))))))
