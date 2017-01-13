(ns ring.middleware.apigw)

(defn- generate-query-string [params]
  (clojure.string/join "&" (map (fn [[k v]] (str (name k) "=" v)) params)))

(defn wrap-apigw-lambda-proxy [handler]
  (fn [apigw-request]
    (let [ring-request {:uri (:path apigw-request)
                        :query-string (generate-query-string (:queryStringParameters apigw-request))
                        :request-method :get}
          response (handler ring-request)]
      {:statusCode (:status response)
       :headers (:headers response)
       :body (:body response)})))
