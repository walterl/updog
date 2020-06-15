(ns updog.edn-versions-db
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log]))

(defmethod ig/init-key ::versions-db
  [_ config]
  (log/info "Versions DB:" config)
  {:initialized-versions-db ::versions-db})
