(ns updog.apps-db.edn-db
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [integrant.core :as ig]
            [medley.core :as m]
            [taoensso.timbre :as log]
            [updog.apps-db :refer [AppsDB initialize!]]))

(defn- load-edn-file
  [filename]
  (edn/read-string (slurp filename)))

(defn- spit-edn!
  [filename edn]
  (spit filename (with-out-str (pprint/pprint edn))))

(defn- with-edn-file*
  [filename f & {:keys [save?]}]
  (let [in-edn  (load-edn-file filename)
        out-edn (f in-edn)]
    (when save?
      (spit-edn! filename out-edn))
    out-edn))

(defmacro with-edn-file
  [filename [var-name] & body]
  `(with-edn-file* ~filename (fn [~var-name] ~@body) :save? false))

(defmacro with-updating-edn-file
  [filename [var-name] & body]
  `(with-edn-file* ~filename (fn [~var-name] ~@body) :save? true))

(defrecord EDNAppsDB [filename]
  AppsDB
  (initialize!
    [_]
    ;; Create empty versions DB if it doesn't exist
    (when-not (.exists (io/file filename))
      (log/debugf "Initializing apps DB" filename)
      (spit-edn! filename {:apps {}})))

  (assoc-app!
    [_ app-key app-data]
    (with-updating-edn-file filename [edn]
      (assoc-in edn [:apps app-key] app-data)))

  (assoc-field!
    [_ app-key field value]
    (with-updating-edn-file filename [edn]
      (assoc-in edn [:apps app-key field] value)))

  (dissoc-app!
    [_ app-key]
    (with-updating-edn-file filename [edn]
      (m/dissoc-in edn [:apps app-key])))

  (get-app
    [_ app-key]
    (with-edn-file filename [edn]
      (get-in edn [:apps app-key])))

  (get-all-apps
    [_]
    (with-edn-file filename [edn]
      (:apps edn))))

(defmethod ig/init-key ::apps-db
  [_ config]
  (let [db (map->EDNAppsDB config)]
    (initialize! db)
    db))
