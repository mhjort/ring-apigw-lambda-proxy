(ns ring.middleware.apigw
  (:import [java.net URLEncoder]))

(defn- generate-query-string [params]
  (clojure.string/join "&" (map (fn [[k v]] (str (URLEncoder/encode (name k)) "=" (URLEncoder/encode v))) params)))

(defn wrap-apigw-lambda-proxy
  ([handler] (wrap-apigw-lambda-proxy handler {}))
  ([handler {:keys [scheduled-event-route]}]
   (fn [apigw-request]
     (let [scheduled-event? (= "Scheduled Event" (:detail-type apigw-request))
           ring-request (if scheduled-event?
                          {:uri scheduled-event-route
                           :query-string ""
                           :request-method :get}
                          {:uri (:path apigw-request)
                           :query-string (generate-query-string (:queryStringParameters apigw-request))
                           :request-method :get})
           response (handler ring-request)]
       {:statusCode (:status response)
        :headers (:headers response)
        :body (:body response)}))))
