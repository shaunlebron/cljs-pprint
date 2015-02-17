(defproject cljs-pprint "0.1.0-SNAPSHOT"

  :description "clojure.pprint ported to clojurescript"
  :url "https://github.com/shaunlebron/cljs-pprint"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2850"]
                 [org.omcljs/om "0.8.8"]
                 [sablono "0.3.4"]]

  :plugins [[lein-cljsbuild "1.0.4"]]

  :source-paths ["src/diff"]

  :clean-targets ["out/test/out"
                  "out/test/pprint.test.js"
                  "out/test/pprint.test.js.map"
                  "out/report/out"
                  "out/report/report.js"]

  :cljsbuild
  {:test-commands {"test" ["phantomjs" "out/test/unit-test.js" "out/test/unit-test.html"]}

   :builds
   [{:id "test"
     :source-paths ["src/cljs" "test/cljs"]
     :compiler
     {:output-to  "out/test/pprint.test.js"
      :source-map "out/test/pprint.test.js.map"
      :output-dir "out/test/out"
      :optimizations :whitespace}}

    {:id "report"
     :source-paths ["src/report"]
     :compiler
     {:output-to  "out/report/report.js"
      :output-dir "out/report/out"
      :optimizations :whitespace}}]})
