# ring-apigw-lambda-proxy

[![Build Status](https://travis-ci.org/mhjort/ring-apigw-lambda-proxy.svg?branch=master)](https://travis-ci.org/mhjort/ring-apigw-lambda-proxy)

Ring middleware for handling AWS API Gateway Lamdbda proxy Requests and responses

Note! Currently only GET requests are supported.

## Installation

Add the following to your `project.clj` `:dependencies`:

```clojure
[ring-apigw-lambda-proxy "0.0.2"]
```

## Usage

AWS Lambda JSON Rest API example.

Add `ring/ring-core`, `ring/ring-json`,`compojure`,`lambdada` and `cheshire` dependencies.

```clojure
(ns example
  (:require [uswitch.lambada.core :refer [deflambdafn]]
            [cheshire.core :refer [parse-stream generate-stream]]
            [clojure.java.io :as io]
            [ring.middleware.apigw :refer [wrap-apigw-lambda-proxy]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-params
                                          wrap-json-response]]
            [ring.util.response :as r]
            [compojure.core :refer :all]))

(defroutes app
  (GET "/v1/hello" {params :params}
    (let [name (get params :name "World")]
      (-> (r/response {:message (format "Hello, %s" name)})))))

(def handler (wrap-apigw-lambda-proxy
               (wrap-json-response
                 (wrap-json-params
                   (wrap-params
                     (wrap-keyword-params
                       app))))))

(deflambdafn example.LambdaFn [is os ctx]
  (with-open [writer (io/writer os)]
    (let [request (parse-stream (io/reader is :encoding "UTF-8") true)]
      (generate-stream (handler request) writer))))

```

It is a common to use AWS Scheduled Events to warmup the Lambda function.
For this case `ring-apigw-lambda-proxy` provides a configuration where
Scheduled Event can be mapped to regular Ring GET route like this:

```clojure
(wrap-apigw-lambda-proxy app {:scheduled-event-route "/warmup"})

(defroutes app
  (GET "/warmup" request {:status 200 :body "Scheduled event for warmup"}))

```

If you have not configured `:scheduled-event-route` and Lambda function is
called via Scheduled Event the error will be thrown.
