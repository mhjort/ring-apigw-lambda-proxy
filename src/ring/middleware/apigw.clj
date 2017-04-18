(ns ring.middleware.apigw
  (:require [ring-apigw-lambda-proxy.core :refer [apigw->ring-request ring-response->apigw]]))

(defn wrap-apigw-lambda-proxy
  ([handler] (wrap-apigw-lambda-proxy handler {}))
  ([handler {:keys [scheduled-event-route]}]
   (fn [request]
     (let [response (handler (apigw->ring-request request scheduled-event-route))]
       (ring-response->apigw response)))))
