(ns ring.middleware.apigw-test
  (:require [clojure.test :refer :all]
            [ring.middleware.apigw :refer [wrap-apigw-lambda-proxy]]))

(deftest query-string-is-url-encoded
  (let [handler (fn [request] {:body request :status 200 :headers nil})
        app (wrap-apigw-lambda-proxy handler)
        request {:path "/v1/test"
                 :queryStringParameters {(keyword "text") "hello, world!"
                                         (keyword "foo[]") "bar"}}
        query-string (get-in (app request) [:body :query-string])]
    (is (= query-string "text=hello%2C+world%21&foo%5B%5D=bar"))))
