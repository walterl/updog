(ns updog.system
  (:require [integrant.core :as ig]))

(defn init
  "Initialize the duct system for the application, from `config-file`."
  [config-file]
  (let [config (-> (slurp config-file)
                   ig/read-string)]
    (ig/load-namespaces config)
    (-> config
        ig/prep
        ig/init)))

(defn halt!
  "Halt the specified system. Ignores nil values."
  [system]
  (when system
    (ig/halt! system)))
