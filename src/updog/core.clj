(ns updog.core
  (:require [integrant.core :as ig])
  (:gen-class))

(defn -main
  [& args]
  (-> (slurp (or (first args) "config.edn"))
      ig/read-string
      ig/prep
      ig/init))
