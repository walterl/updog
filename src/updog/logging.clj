(ns updog.logging
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log]))

(defmethod ig/init-key ::timbre-logger
  [_ {:keys [log-level]}]
  (log/set-level! log-level)
  (log/debug "Log level set to" log-level))
