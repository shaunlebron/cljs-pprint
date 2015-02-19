(ns report.core
  (:require
    [om.core :as om]
    [om-tools.core :refer-macros [defcomponent]]
    [sablono.core :refer-macros [html]]
    [figwheel.client :as fw]))

(enable-console-print!)

(defonce app-state
  (atom {}))

(defcomponent page
  [app owner]
  (render [_this]
    (html
      [:div "YO"])))

(om/root
  page
  app-state
  {:target (. js/document (getElementById "app"))})

(fw/start {})
