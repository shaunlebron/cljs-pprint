(ns report.core
  (:require
    [om.core :as om]
    [om.dom :as dom]
    [om-tools.core :refer-macros [defcomponent]]
    [sablono.core :refer-macros [html]]
    [figwheel.client :as fw]
    [ajax.core :refer [GET]]))

(enable-console-print!)

(def forms (atom nil))

(def clj->cljs-defs
  {"with-pretty-writer" ["with-pretty-writer"]})

(defn page []
  (html
    [:div "YO"]))

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
