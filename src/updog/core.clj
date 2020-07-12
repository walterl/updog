(ns updog.core
  (:require [integrant.core :as ig]
            [updog.updater :as updater])
  (:gen-class))

(defn -main
  [& args]
  (let [config-file (or (first args) "config.edn")
        config      (-> (slurp config-file)
                        ig/read-string)
        _           (ig/load-namespaces config)
        system      (-> config
                        ig/prep
                        ig/init)]
    (updater/start-update! (:updog.updater/single-run-updater system))
    (ig/halt! system)))
