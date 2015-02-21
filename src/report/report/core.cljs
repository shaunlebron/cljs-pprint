(ns report.core
  (:require
    [clojure.string :refer [join]]
    [om.core :as om]
    [om.dom :as dom]
    [om-tools.core :refer-macros [defcomponent]]
    [sablono.core :refer-macros [html]]
    [figwheel.client :as fw]
    [ajax.core :refer [GET]]))

(enable-console-print!)

(def forms (atom nil))

(def clj->cljs-defs
  {"with-pretty-writer" nil
   "getf" nil
   "setf" nil
   "pretty-writer" nil
   "pprint" ["pprint*" "pprint-sb" "pprint-str" "pprint"]
   "pretty-writer?" nil
   "make-pretty-writer" nil
   "*print-right-margin*" nil
   "*print-miser-width*" nil
   "*print-pretty*" nil
   "PrettyFlush" "IPrettyFlush"
   "*default-page-width*" nil
   "get-field" nil
   "set-field" nil
   "get-column" nil
   "get-line" nil
   "get-max-column" nil
   "set-max-column" nil
   "get-writer" nil
   "c-write-char" nil
   "column-writer" nil
   "logical-block" nil
   "write-initial-lines" nil})

(defn func-head
  [form]
  (let [name- (:key form)
        filename (:filename form)
        lines (join "-" (:lines form))]
    [:div.func-head
     [:span.func-name name-] " @ " filename " : " lines]))

(defn group
  [[orig-name p]]
  (let [orig (get-in @forms [:clj orig-name])
        p (or p orig-name)
        port-names (if (sequential? p) p [p])
        ports (map #(get-in @forms [:cljs %]) port-names)]
    (list
      [:tr.header
       [:td [:a {:name orig-name} (func-head orig)]]
       [:td (map func-head ports)]]
      [:tr.code
       [:td [:pre (:source orig)]]
       [:td [:pre (join "\n\n" (map :source ports))]]])))

(defn page []
  (html
    [:div
     [:div.header
      [:h1 "clojure.pprint - ClojureScript port "
       [:a {:href "https://github.com/shaunlebron/cljs-pprint"} "(on github)"]]
      [:p
       (str "Data is at the core of any ClojureScript application, of course. "
            "Users should be able to log this data in a readable format. "
            "To this end, we are porting ")
       [:a {:href "https://clojure.github.io/clojure/clojure.pprint-api.html"}
            "clojure.pprint"]
       (str " to ClojureScript.  This is a bit of a large task, so this page was created to help track its progress.")]
      [:p
       "(Also check out "
       [:a {:href "https://github.com/brandonbloom/fipp/issues/7"} "fipp"]
       " which is a faster, more idiomatic EDN printer being ported to ClojureScript.  And check out "
       [:a {:href "https://github.com/binaryage/cljs-devtools-sample"} "cljs-devtools"]
       ", an awesome solution for increasing visibility of ClojureScript data in the Chrome console.)"]
      [:p
       "Below, we list all clojure.pprint functions and other defs that have to be ported. "
       "There is also a side-by-side comparison between the clojure and clojurescript functions currently implemented."]]
     [:table.toc
      [:tr
       (for [[filename defs] (:clj-files @forms)]
         [:td
          [:h1 filename]
          [:table.defs
           (for [d defs]
             [:tr [:td.num (get-in @forms [:clj d :lines 0])]
                  [:td
                   (if (contains? clj->cljs-defs d)
                     [:a.toc-link {:href (str "#" d)} d]
                     d)]])]])]]
     [:table.code-table
      [:tr
       [:td "CLOJURE"]
       [:td "CLOJURESCRIPT"]]
      (map group clj->cljs-defs)]]))

(defn re-render
  []
  (dom/render (page) (. js/document (getElementById "app"))))

(defn main
  []
  (GET "forms.edn" {:response-format :edn
                    :handler #(do (reset! forms %) (re-render))}))

(fw/start
  {:on-jsload main})

(.addEventListener js/window "load" main)
