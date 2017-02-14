(ns ring.middleware.apigw-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [cheshire.core :refer [parse-string]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.apigw :refer [wrap-apigw-lambda-proxy]]))

(defroutes app-routes
  (GET "/warmup" request {:status 200 :body "warmup"})
  (route/not-found {:status 404 :body {:errors "Route not found"}}))

(def ring-routes
  (-> app-routes
      wrap-keyword-params
      wrap-params
      wrap-json-response))

(deftest query-string-is-url-encoded
  (let [handler (fn [request] {:body request :status 200 :headers nil})
        app (wrap-apigw-lambda-proxy handler)
        request {:path "/v1/test"
                 :queryStringParameters {(keyword "text") "hello, world!"
                                         (keyword "foo[]") "bar"}}
        query-string (get-in (app request) [:body :query-string])]
    (is (= query-string "text=hello%2C+world%21&foo%5B%5D=bar"))))

(deftest scheduled-event-is-handled
  (let [app (wrap-apigw-lambda-proxy ring-routes {:scheduled-event-route "/warmup"})
        result (app (-> (io/resource "scheduled-event.json")
                        (slurp)
                        (parse-string true)))]
    (is (= {:statusCode 200 :headers {} :body "warmup"} result))))
