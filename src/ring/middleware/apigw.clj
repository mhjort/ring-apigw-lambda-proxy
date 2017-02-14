(ns ring.middleware.apigw
  (:require [clojure.string :as string])
  (:import [java.net URLEncoder]))

(defn- generate-query-string [params]
  (string/join "&" (map (fn [[k v]]
                          (str (URLEncoder/encode (name k)) "=" (URLEncoder/encode v)))
                        params)))

(defn- apigw-get [uri query-string]
  {:uri uri
   :query-string query-string
   :request-method :get})

(defn- no-scheduled-route-configured-error [request]
  (throw (ex-info "Got Scheduled Event but no scheduled-event-route configured"
                  {:request request})))

(defn- apigw->ring-request [request scheduled-event-route]
  (let [scheduled-event? (= "Scheduled Event" (:detail-type request))]
    (cond
      (and scheduled-event? scheduled-event-route) (apigw-get scheduled-event-route "")
      scheduled-event? (no-scheduled-route-configured-error request)
      :else (apigw-get (:path request)
                       (generate-query-string (:queryStringParameters request))))))

(defn wrap-apigw-lambda-proxy
  ([handler] (wrap-apigw-lambda-proxy handler {}))
  ([handler {:keys [scheduled-event-route]}]
   (fn [request]
     (let [response (handler (apigw->ring-request request
                                                  scheduled-event-route))]
       {:statusCode (:status response)
        :headers (:headers response)
        :body (:body response)}))))
