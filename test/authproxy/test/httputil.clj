(ns authproxy.test.httputil
  (:use [clojure.test])
  (:use [authproxy.httputil]))

(deftest test-host-value
  (let [request { :unused "abc" :headers { "host" "localhost:8080" "user-agent" "somegarbage" }}]
    (is (= (host-value request) "localhost:8080"))))

(deftest test-query-string
  (let [withquery { :uri "/somestuff" :query-string "foo=123&bar=abc" }
        noquery { :uri "/somestuff" }]
    (is (= (query-string noquery) ""))
    (is (= (query-string withquery) "?foo=123&bar=abc"))))

(deftest test-header-list-to-string
  (let [values (java.util.ArrayList. 5)]
    (doseq [s "abcde"] (.add values s))
    (is (= (header-list-to-string values) "a, b, c, d, e"))))

(deftest test-http-method
  (is (= "GET" (http-method { :request-method :get })))
  (is (= "POST" (http-method { :request-method :post })))
  (is (= "PUT" (http-method { :request-method :put }))))

(deftest test-convert-header-map
  (let [headers (java.util.HashMap.)
        mimetypes (java.util.ArrayList. 2)
        content-length (java.util.ArrayList. 1)]
    (doseq [s ["text/html" "text/csv"]] (.add mimetypes s))
    (.add content-length "100")
    (doto headers
      (.put "Accept" mimetypes)
      (.put "Content-Length" content-length))
    (let [hmap (convert-header-map headers)]
      (is (= (get hmap "Accept") "text/html, text/csv"))
      (is (= (get hmap "Content-Length") "100")))))

(deftest test-cappend
  (let [v1 nil
        v2 "B"]
    (is (= (cappend "a" ["&" v1] ["&" v2] "&c")
           "a&B&c"))))

(deftest test-request-url
  (is (= (request-url { :scheme "http" :server-name "localhost" :server-port 8080 :uri "/somecontext/somefile.html" :query-string nil })
         "http://localhost:8080/somecontext/somefile.html"))
  (is (= (request-url { :scheme "https" :server-name "localhost" :uri "/somecontext/somefile.html" :query-string "foo=bar" })
         "https://localhost/somecontext/somefile.html?foo=bar")))

