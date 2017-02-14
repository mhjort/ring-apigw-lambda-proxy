(defproject ring-apigw-lambda-proxy "0.0.2"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:dev {:dependencies [[cheshire "5.7.0"]
                                  [compojure "1.5.2"]
                                  [ring/ring-json "0.4.0"]]}})

