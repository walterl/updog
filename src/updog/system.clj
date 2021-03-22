(ns updog.system
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]))

(defn load-config!
  [{:keys [filename db-file log-level log-format]}]
  (let [config (-> (io/resource (or filename "system_config.edn"))
                   slurp
                   ig/read-string
                   (cond->
                     db-file    (assoc-in [:updog.apps-db.edn-db/apps-db :filename] db-file)
                     log-level  (assoc-in [:updog.logging/timbre-logger :log-level] log-level)
                     log-format (assoc-in [:updog.logging/timbre-logger :use-default-output-fn?]
                                          (= log-format :long))))]
    (ig/load-namespaces config)
    config))

(defn init
  "Initialize the application integrant system, using app `db-file`."
  [options]
  (-> (load-config! options)
      ig/prep
      ig/init))

(defn halt!
  "Halt the specified system. Ignores nil values."
  [system]
  (when system
    (ig/halt! system)))
