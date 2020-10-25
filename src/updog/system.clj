(ns updog.system
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]))

(defn init
  "Initialize the application integrant system, using app `db-file`."
  [{:keys [db-file log-level]}]
  (let [config (cond-> (-> (io/resource "system_config.edn") slurp ig/read-string)
                 db-file   (assoc-in [:updog.apps-db.edn-db/apps-db :filename] db-file)
                 log-level (assoc-in [:updog.logging/timbre-logger :log-level] log-level))]
    (ig/load-namespaces config)
    (-> config
        ig/prep
        ig/init)))

(defn halt!
  "Halt the specified system. Ignores nil values."
  [system]
  (when system
    (ig/halt! system)))
