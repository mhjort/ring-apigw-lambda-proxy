(ns ring.middleware.apigw-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [cheshire.core :refer [parse-string]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.apigw :refer [wrap-apigw-lambda-proxy]])
  (:import [clojure.lang ExceptionInfo]))

(def scheduled-event
  (-> (io/resource "scheduled-event.json")
      (slurp)
      (parse-string true)))

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
                                         (keyword "foo[]") "bar"}
                 :headers {"X-Forwarded-For" "127.0.0.1, 127.0.0.2"
                           "Accept-Language" "en-US,en;q=0.8"}}
        query-string (get-in (app request) [:body :query-string])]
    (is (= query-string "text=hello%2C+world%21&foo%5B%5D=bar"))))

(deftest when-called-with-scheduled-event

  (testing "maps to given route"
    (let [app (wrap-apigw-lambda-proxy ring-routes {:scheduled-event-route "/warmup"})]
      (is (= {:statusCode 200 :headers {} :body "warmup"}
             (app scheduled-event))))

  (testing "throws exception if route is not configured"
    (let [app (wrap-apigw-lambda-proxy ring-routes)]
      (is (thrown? ExceptionInfo (app scheduled-event)))))))

(deftest append-request-headers-to-ring-request
  (let [request-header {"X-Forwarded-For" "127.0.0.1, 127.0.0.2"
                        "Accept-Language" "en-US,en;q=0.8"}
        handler (fn [request] {:body request :status 200 :headers nil})
        app (wrap-apigw-lambda-proxy handler)
        request {:path "/v2/test"
                 :queryStringParameters {}
                 :headers request-header}
        headers-from-request (get-in (app request) [:body :headers])]
    (is (= headers-from-request request-header))))

