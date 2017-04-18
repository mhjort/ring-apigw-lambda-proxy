(ns ring-apigw-lambda-proxy.core
  (:require [clojure.string :as string])
  (:import [java.io ByteArrayInputStream]
           [java.net URLEncoder]))

(defn- generate-query-string [params]
  (string/join "&" (map (fn [[k v]]
                          (str (URLEncoder/encode (name k)) "=" (URLEncoder/encode v)))
                        params)))

(defn- request->http-method [request]
  (-> (:httpMethod request)
      (string/lower-case)
      (keyword)))

(defn- apigw-request->ring-request [apigw-request]
  {:pre [(every? #(contains? apigw-request %) [:httpMethod :path :queryStringParameters])
         (contains? #{"GET" "POST" "OPTIONS" "DELETE" "PUT"} (:httpMethod apigw-request))]}
  {:uri (:path apigw-request)
   :query-string (generate-query-string (:queryStringParameters apigw-request))
   :request-method (request->http-method apigw-request)
   :headers (:headers apigw-request)
   :body (when-let [body (:body apigw-request)] (ByteArrayInputStream. (.getBytes body "UTF-8")))})

(defn- no-scheduled-route-configured-error [request]
  (throw (ex-info "Got Scheduled Event but no scheduled-event-route configured"
                  {:request request})))

(defn apigw->ring-request [request scheduled-event-route]
  (let [scheduled-event? (= "Scheduled Event" (:detail-type request))]
    (cond
      (and scheduled-event? scheduled-event-route) (apigw-request->ring-request {:path scheduled-event-route
                                                                                 :queryStringParameters ""
                                                                                 :headers nil
                                                                                 :httpMethod "GET"})
      scheduled-event? (no-scheduled-route-configured-error request)
      :else (apigw-request->ring-request request))))

(defn ring-response->apigw [response]
  {:statusCode (:status response)
   :headers (:headers response)
   :body (:body response)})

(defmacro deflambda-ring-handler [name handler & [scheduled-event-route]]
  `(uswitch.lambada.core/deflambdafn ~name [is# os# ctx#]
     (with-open [writer# (clojure.java.io/writer os#)]
       (let [request# (cheshire.core/parse-stream (io/reader is# :encoding "UTF-8") keyword)
             response# (-> request# (apigw->ring-request ~scheduled-event-route) ~handler ring-response->apigw)]
         (cheshire.core/generate-stream response# writer#)))))
