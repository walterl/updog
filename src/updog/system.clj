(ns updog.system
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]))

(defn init
  "Initialize the application integrant system, using app `db-file`."
  [db-file]
  (let [config (-> (slurp (io/resource "system_config.edn"))
                   ig/read-string
                   (assoc-in [:updog.apps-db.edn-db/apps-db :filename] db-file))]
    (ig/load-namespaces config)
    (-> config
        ig/prep
        ig/init)))

(defn halt!
  "Halt the specified system. Ignores nil values."
  [system]
  (when system
    (ig/halt! system)))
