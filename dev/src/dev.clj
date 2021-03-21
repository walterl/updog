(ns dev
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [integrant.repl :as ig.repl :refer [clear go halt init prep reset]]
            [integrant.repl.state :refer [config system]]
            [updog.system :as sys]))

(clojure.tools.namespace.repl/set-refresh-dirs "dev/src" "src" "test")

(def dev-config
  (atom {}))

(ig.repl/set-prep! #(deref dev-config))

(defn load-config!
  [opts]
  (let [config (sys/load-config! opts)]
    (reset! dev-config config)
    config))
