(defproject cljs-pprint "0.1.0-SNAPSHOT"

  :description "clojure.pprint ported to clojurescript"
  :url "https://github.com/shaunlebron/cljs-pprint"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2665"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [hiccups "0.3.0"]
                 [fipp "0.5.2"]
                 [figwheel "0.2.3-SNAPSHOT"]
                 [cljs-ajax "0.3.10"]
                 [markdown-clj "0.9.62"]
                 [cljsjs/jquery "1.9.0-0"]]

  :plugins [[lein-cljsbuild "1.0.4"]
            [lein-figwheel "0.2.3-SNAPSHOT"]
            [com.cemerick/clojurescript.test "0.3.3"]]

  :source-paths ["src/parse"]
  :main parse.core

  :clean-targets ^{:protect false} ["resources/test/out"
                                    "resources/test/pprint.test.js"
                                    "resources/test/pprint.test.js.map"
                                    "resources/report/js/out"
                                    "resources/report/js/report.js"]

  :cljsbuild
  {:test-commands {"test" ["phantomjs" :runner "resources/test/pprint.test.js"]}

   :builds
   [{:id "test"
     :source-paths ["src/cljs" "test/cljs"]
     :compiler
     {:output-to  "resources/test/pprint.test.js"
      :source-map "resources/test/pprint.test.js.map"
      :output-dir "resources/test/out"
      :optimizations :whitespace}}

    {:id "report"
     :source-paths ["src/report" "src/report_dev"]
     :compiler
     {:output-to  "resources/report/js/report.js"
      :output-dir "resources/report/js/out"
      :optimizations :none
      :source-map true}}

    {:id "report-prod"
     :source-paths ["src/report"]
     :compiler
     {:output-to  "resources/report/js/report-prod.js"
      :output-dir "resources/report/js/out-prod"
      :optimizations :advanced
      :externs ["externs/highlight.ext.js"]}}
    
    ]}

  :figwheel
  {:http-server-root "report"
   :css-dirs "resources/report"}

  )
