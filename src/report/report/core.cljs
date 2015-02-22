(ns report.core
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]
    [hiccups.core :refer [html defhtml]])
  (:require
    [cljs.core.async :refer [chan <! put! close!]]
    [clojure.string :refer [join]]
    [hiccups.runtime]
    [figwheel.client :as fw]
    [ajax.core :refer [GET]]
    [markdown.core :refer [md->html]]
    [cljsjs.jquery]))

(enable-console-print!)

(def forms nil)
(def progress nil)
(def welcome nil)

(defn welcome-section
  []
  [:div.header
   (md->html welcome
             :reference-links? true)])

(defn file-toc-section
  [filename defs]
  (list
    [:h3 filename]
    [:table.def-table
     (for [d defs]
       [:tr [:td.num (get-in forms [:clj d :lines 0])]
        [:td
         (if (contains? progress d)
           [:a.toc-link {:href (str "#" d)} d]
           d)]])]))

(defn toc-section
  []
  [:div.toc
   [:h2 "Progress"]
   [:p "These are the original clojure.pprint files and respective defs that need to be ported. Line numbers are displayed too."]
   [:p "The " [:span.toc-link "green names"] " are currently ported; " [:u "click them"] " to see the original and ported versions together."]
   [:table.file-table
    [:tr
     (for [[filename defs] (sort-by first (:clj-files forms))]
       [:td (file-toc-section filename defs)])]]])

(defn func-head
  ([form] (func-head form false))
  ([form create-anchor?]
   (let [name- (:key form)
         filename (:filename form)
         lines (join "-" (:lines form))
         name-content [:span.func-name name-]]
     [:div.func-head
      (if create-anchor?
        [:a.def-anchor {:name name- :href (str "#" name-)} name-content]
        name-content)
      " @ " filename " : " lines])))

(defn code-block
  [form]
  [:table.code-block
   [:tr
    [:td.lines [:pre [:code
                      (join "\n" (range (-> form :lines first)
                                        (-> form :lines second inc)))]]]
    [:td [:pre [:code.clojure (:source form)]]]]])

(defn code-compare-def
  [[orig-name p]]
  (let [orig (get-in forms [:clj orig-name])
        p (if (= :same-name p) orig-name p)
        port-names (if (sequential? p) p [p])
        ports (map #(get-in forms [:cljs %]) port-names)]
    (list
      [:tr.header
       [:td (func-head orig true)]
       [:td (map func-head ports)]]
      [:tr.code
       [:td (code-block orig)]
       [:td (map code-block ports)]])))

(defn code-compare-section
  []
  [:div.code-compare-section
   [:table.code-compare-table
    [:tr.header-row
     [:td [:h1 "Clojure"] "original clojure.pprint functions"]
     [:td [:h1 "ClojureScript"] "new ported functions"]]
    (map code-compare-def progress)]])

(defn page []
  (html
    [:div
     (welcome-section)
     (toc-section)
     (code-compare-section)]))

(defn highlight-code!
  []
  (.each (js/$ "pre code")
         (fn [i block]
           (.highlightBlock js/hljs block))))

(defn force-hash-nav!
  []
  (let [h (aget js/location "hash")]
    (aset js/location "hash" "")
    (aset js/location "hash" h)))

(defn re-render
  []
  (let [e (. js/document (getElementById "app"))]
    (aset e "innerHTML" (page)))
  (highlight-code!)
  (force-hash-nav!))

(defn get-async
  [url res-format]
  (let [c (chan)
        handler (fn [data] (put! c data) (close! c))]
    (GET url {:response-format res-format :handler handler})
    c))

(defn fetch-all
  [urls]
  (->> urls
       (map #(get-async (first %) (second %)))
       (zipmap (keys urls))))

(defn main
  []
  (let [downloads (fetch-all {"forms.edn" :edn
                              "progress.edn" :edn
                              "welcome.md" :raw})]
    (go
      (set! forms    (<! (get downloads "forms.edn")))
      (set! progress (<! (get downloads "progress.edn")))
      (set! welcome  (<! (get downloads "welcome.md")))
      (re-render))))

(fw/start
  {:on-jsload main})

(.addEventListener js/window "load" main)
