(ns updog.logging
  (:require [integrant.core :as ig]
            [taoensso.encore :as enc]
            [taoensso.timbre :as log]))

(def ^:private level-marker
  {:trace  "T"
   :debug  "*"
   :info   "."
   :warn   "?"
   :error  "!"
   :fatal  "!!"
   :report "R"})

(defn- simple-output-fn
  "Adapted from taoensso.timbre/default-output-fn for simpler output format."
  ([     data] (simple-output-fn nil data))
  ([opts data] ; For partials
   (let [{:keys [no-stacktrace?]} opts
         {:keys [level ?err msg_]} data]
     (str
      "[" (get level-marker level "x") "] "
      (force msg_)
      (when-not no-stacktrace?
        (when-let [err ?err]
          (str enc/system-newline (log/stacktrace err opts))))))))

(defmethod ig/init-key ::timbre-logger
  [_ {:keys [log-level use-default-output-fn?]}]
  (when-not use-default-output-fn?
    (log/set-config! (assoc log/*config* :output-fn simple-output-fn)))
  (when log-level
    (log/set-level! log-level)))
