(ns updog.updater
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log]))

(defmethod ig/init-key ::single-run-updater
  [_ config]
  (log/info "Single run updater:" config)
  {:initialized-updater ::single-run-updater})
