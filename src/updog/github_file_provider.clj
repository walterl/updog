(ns updog.github-file-provider
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log]))

(defmethod ig/init-key ::file-provider
  [_ config]
  (log/info "File provider:" config)
  {:initialized-provider ::file-provider})
