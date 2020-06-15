(ns dev
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [integrant.repl :as ig.repl :refer [clear go halt init prep reset]]
            [integrant.repl.state :refer [config system]]))

(clojure.tools.namespace.repl/set-refresh-dirs "dev/src" "src" "test")

(def dev-config
  (atom {}))

(ig.repl/set-prep! #(deref dev-config))

(defn load-config!
  [filename]
  (let [config (ig/read-string (slurp filename))]
    (ig/load-namespaces config)
    (reset! dev-config config)))
