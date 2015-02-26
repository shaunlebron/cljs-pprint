(ns report.dev
  (:require
    [figwheel.client :as fw]
    [report.core :as core]))

(fw/start
  {:on-jsload core/main})
