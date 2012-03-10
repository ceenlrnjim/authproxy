(ns authproxy.httputil)

(defn host-value
  "Returns the host header value from the specified request"
  [req]
  (get (:headers req) "host"))

(defn query-string
  [req]
  (if (nil? (:query-string req)) ""
    (str "?" (:query-string req))))

(defn header-list-to-string
  [value-list]
  (reduce 
    #(str %1 (if (= %1 "") "" ", ") %2)
    ""
    value-list))

(defn http-method
  [req]
  (.toUpperCase (name (:request-method req))))

(defn convert-header-map
  [headers]
  (dissoc 
    (reduce
      #(assoc %1 (.getKey %2) (header-list-to-string (.getValue %2)))
      {}
      headers)
    nil)) ; remove the HTTP 1.1/200 OK line 

(defn cappend
  "Conditional append - if second member of each vector is non-nil both are appended to the string"
  [& pairs]
  (reduce
    #(if (vector? %2) 
         (if (not (nil? (second %2))) 
             (str %1 (first %2) (second %2)) 
             %1)
        (str %1 %2))
    ""
    pairs))

(defn request-url
  [req]
  (cappend (name (:scheme req)) "://" (:server-name req) [":" (:server-port req)] (:uri req) ["?" (:query-string req)]))

