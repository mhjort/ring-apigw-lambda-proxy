(ns ring.middleware.apigw
  (:require [clojure.string :as string])
  (:import [java.net URLEncoder]))

(defn- generate-query-string [params]
  (string/join "&" (map (fn [[k v]]
                          (str (URLEncoder/encode (name k)) "=" (URLEncoder/encode v)))
                        params)))

(defn- request->http-method [request]
  (-> (:httpMethod request)
      (string/lower-case)
      (keyword)))

(defn- keyword->lowercase-string [k]
  (string/lower-case (name k)))

(defn- map-keys [f m]
  (into {} (map (fn [[k v]] [(f k) v]) m)))

(defn- apigw-request->ring-request [apigw-request]
  {:pre [(every? #(contains? apigw-request %) [:httpMethod :path :queryStringParameters])
         (contains? #{"GET" "POST" "OPTIONS" "DELETE" "PUT"} (:httpMethod apigw-request))]}
  {:uri (:path apigw-request)
   :query-string (generate-query-string (:queryStringParameters apigw-request))
   :request-method (request->http-method apigw-request)
   :headers (map-keys keyword->lowercase-string (:headers apigw-request))})

(defn- no-scheduled-route-configured-error [request]
  (throw (ex-info "Got Scheduled Event but no scheduled-event-route configured"
                  {:request request})))

(defn- apigw->ring-request [request scheduled-event-route]
  (let [scheduled-event? (= "Scheduled Event" (:detail-type request))]
    (cond
      (and scheduled-event? scheduled-event-route) (apigw-request->ring-request {:path scheduled-event-route
                                                                                 :queryStringParameters ""
                                                                                 :headers nil
                                                                                 :httpMethod "GET"})
      scheduled-event? (no-scheduled-route-configured-error request)
      :else (apigw-request->ring-request request))))

(defn wrap-apigw-lambda-proxy
  ([handler] (wrap-apigw-lambda-proxy handler {}))
  ([handler {:keys [scheduled-event-route]}]
   (fn [request]
     (let [response (handler (apigw->ring-request request scheduled-event-route))]
       {:statusCode (:status response)
        :headers (:headers response)
        :body (:body response)}))))
