(defproject cljs-pprint "0.1.0-SNAPSHOT"

  :description "clojure.pprint ported to clojurescript"
  :url "https://github.com/shaunlebron/cljs-pprint"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2665"]
                 [org.omcljs/om "0.8.8"]
                 [prismatic/om-tools "0.3.10"]
                 [sablono "0.3.4"]
                 [fipp "0.5.2"]
                 [figwheel "0.2.3-SNAPSHOT"]
                 [cljs-ajax "0.3.10"]]

  :plugins [[lein-cljsbuild "1.0.4"]
            [lein-figwheel "0.2.3-SNAPSHOT"]]

  :source-paths ["src/parse"]
  :main parse.core

  :clean-targets ["resources/test/out"
                  "resources/test/pprint.test.js"
                  "resources/test/pprint.test.js.map"
                  "resources/report/out"
                  "resources/report/report.js"]

  :cljsbuild
  {:test-commands {"test" ["phantomjs" "resources/test/unit-test.js" "resources/test/unit-test.html"]}

   :builds
   [{:id "test"
     :source-paths ["src/cljs" "test/cljs"]
     :compiler
     {:output-to  "resources/test/pprint.test.js"
      :source-map "resources/test/pprint.test.js.map"
      :output-dir "resources/test/out"
      :optimizations :whitespace}}

    {:id "report"
     :source-paths ["src/report"]
     :compiler
     {:main core
      :output-to  "resources/report/report.js"
      :output-dir "resources/report/out"
      :optimizations :none
      :source-map true}}]}

  :figwheel
  {:http-server-root "report"
   :css-dirs "resources/report"}

  )
