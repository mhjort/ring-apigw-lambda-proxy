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
  (GET "/get" request {:status 200 :body "get"})
  (OPTIONS "/options" request {:status 200 :body "options"})
  (POST "/post" request {:status 200 :body "post"})
  (PATCH "/patch" request {:status 200 :body "patch"})
  (PUT "/put" request {:status 200 :body "put"})
  (DELETE "/delete" request {:status 200 :body "delete"})
  (route/not-found {:status 404 :body {:errors "Route not found"}}))

(def ring-routes
  (-> app-routes
      wrap-keyword-params
      wrap-params
      wrap-json-response))

(defn ->apigw-request [http-method path]
  {:httpMethod http-method
   :path path
   :queryStringParameters {(keyword "text") "hello, world!"
                           (keyword "foo[]") "bar"}
   :headers {"X-Forwarded-For" "127.0.0.1, 127.0.0.2"
             "Accept-Language" "en-US,en;q=0.8"}})

(deftest query-string-is-url-encoded
  (let [handler (fn [request] {:body request :status 200 :headers nil})
        app (wrap-apigw-lambda-proxy handler)
        request (->apigw-request "GET" "/v1/test")
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
  (let [request-header {:X-Forwarded-For "127.0.0.1, 127.0.0.2"
                        :Accept-Language "en-US,en;q=0.8"}
        handler (fn [request] {:body request :status 200 :headers nil})
        app (wrap-apigw-lambda-proxy handler)
        request (assoc (->apigw-request "GET" "/v2/test") :headers request-header)
        headers-from-request (get-in (app request) [:body :headers])]
    (is (= headers-from-request {"x-forwarded-for" "127.0.0.1, 127.0.0.2"
                                 "accept-language" "en-US,en;q=0.8"}))))

(deftest append-body-input-stream-to-ring-request
  (let [handler (fn [request] {:body request :status 200})
        app (wrap-apigw-lambda-proxy handler)
        request (assoc (->apigw-request "GET" "/2/test") :body "foo")
        body-from-request (get-in (app request) [:body :body])]
    (is (= "foo" (slurp body-from-request)))))

(deftest when-calling-with-different-http-methods
  (let [app (wrap-apigw-lambda-proxy ring-routes {:scheduled-event-route "/warmup"})]

    (testing "GET"
      (is (= {:statusCode 200 :headers {} :body "get"}
             (app (->apigw-request "GET" "/get")))))

    (testing "OPTIONS"
      (is (= {:statusCode 200 :headers {} :body "options"}
             (app (->apigw-request "OPTIONS" "/options")))))

    (testing "POST"
      (is (= {:statusCode 200 :headers {} :body "post"}
             (app (->apigw-request "POST" "/post")))))

    (testing "PATCH"
      (is (= {:statusCode 200 :headers {} :body "patch"}
             (app (->apigw-request "PATCH" "/patch")))))

    (testing "PUT"
      (is (= {:statusCode 200 :headers {} :body "put"}
             (app (->apigw-request "PUT" "/put")))))

    (testing "DELETE"
      (is (= {:statusCode 200 :headers {} :body "delete"}
             (app (->apigw-request "DELETE" "/delete")))))))

(deftest when-calling-with-invalid-gw-payload
  (let [app (wrap-apigw-lambda-proxy ring-routes {:scheduled-event-route "/warmup"})]

    (testing "missing key in gw-payload"
      (is (thrown-with-msg? AssertionError #"Assert failed: \(every\?" (app (dissoc (->apigw-request "GET" "/failing") :httpMethod)))))

    (testing "invalid http method"
      (is (thrown-with-msg? AssertionError #"Assert failed: \(contains\? #\{\"DELETE\"" (app (->apigw-request "TEAPOT" "/failing")))))))
