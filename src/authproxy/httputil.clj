(ns authproxy.httputil)

(defn host-value
  "Returns the host header value from the specified request"
  [req]
  (get (:headers req) "host"))

(defn query-string
  "If the query string for the specified request is nil, returns empty string.
  Otherwise returns '?' and the contents of the query string value"
  [req]
  (if (nil? (:query-string req)) ""
    (str "?" (:query-string req))))

(defn header-list-to-string
  "Takes a  list (as returned by the value of a header from java.net)
  and converts it to a comma delimited list as in the http header as text"
  [value-list]
  (reduce 
    #(str %1 (if (= %1 "") "" ", ") %2)
    ""
    value-list))

(defn http-method
  "Returns the capitalized string http method that corresponds to the
  ring keyword in the request map.  :get -> 'GET'"
  [req]
  (.toUpperCase (name (:request-method req))))

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

