(defproject cljs-pprint "0.1.0-SNAPSHOT"

  :description "clojure.pprint ported to clojurescript"
  :url "https://github.com/shaunlebron/cljs-pprint"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0-beta2"]
                 [org.clojure/clojurescript "0.0-3211"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [hiccups "0.3.0"]
                 [fipp "0.5.2"]
                 [figwheel "0.2.9"]
                 [cljs-ajax "0.3.10"]
                 [markdown-clj "0.9.62"]
                 [cljsjs/jquery "1.9.0-0"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-figwheel "0.2.9"]]

  :source-paths ["src/parse"]
  :main parse.core

  :clean-targets ^{:protect false} ["resources/test/out"
                                    "resources/test/pprint.test.js"
                                    "resources/test/pprint.test.js.map"
                                    "resources/report/js/out"
                                    "resources/report/js/report.js"]

  :cljsbuild
  {:test-commands {"test" ["node" :runner "resources/test/pprint.test.js"]}

   :builds
   [{:id "test"
     :source-paths ["src/cljs" "test/cljs"]
     :notify-command ["node" "resources/test/pprint.test.js"]
     :compiler
     {:output-to  "resources/test/pprint.test.js"
      :source-map "resources/test/pprint.test.js.map"
      :output-dir "resources/test/out"
      :optimizations :simple}}

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
