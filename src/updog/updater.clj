(ns updog.updater
  (:require [clojure.java.shell :refer [sh]]
            [integrant.core :as ig]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :as fs.comp]
            [taoensso.timbre :as log]
            [updog.apps-db :as apps-db]
            [updog.app-source :as app-source]
            [updog.util :as u]))

(defn- newer-version?
  "Is version `a` newer than `b`?"
  [a b]
  (pos? (compare a b)))

(defmulti post-process
  "Dispatches on app's :post-proc key"
  (fn [{:keys [post-proc], :as _app} _downloaded-path]
    post-proc))

(defmethod post-process :default
  [{:keys [dest-path]} downloaded-path]
  (log/debugf "cp %s %s" downloaded-path dest-path)
  (u/copy! downloaded-path dest-path)
  (log/debugf "chmod u+x %s" dest-path)
  (fs/chmod "u+x" dest-path))

(defmethod post-process :shell-script
  [{:keys [dest-path install-script]} downloaded-path]
  (let [sh-vars {:dl-file   downloaded-path
                 :dest-file dest-path
                 :dest-dir  (fs/parent dest-path)}]
    (log/debugf "shell: %s %s" sh-vars)
    (apply sh (u/command->sh-args install-script sh-vars))))

(defmethod post-process :unzip
  [{:keys [dest-path]} downloaded-path]
  (let [dest-dir (fs/parent dest-path)]
    (log/debugf "unzip %s %s" downloaded-path dest-dir)
    (fs.comp/unzip downloaded-path dest-dir)))

(defn- source-of-type
  [sources source-type]
  (or (first (filter #(= source-type (app-source/source-type %))
                     sources))
      (throw (ex-info "Source not found!" {:source-type source-type
                                           :sources     sources}))))

(defn- update-file
  [db src
   {current-version :version, app-key :app-key, app-name :name, :as app}
   {latest-version :version, :as latest-version-info}]
  (if (newer-version? latest-version current-version)
    (let [tmp-dest (u/temp-file-path)]
      (log/infof "App %s %s has newer version: %s"
                 app-name
                 current-version
                 latest-version)
      (app-source/download! src latest-version-info tmp-dest)
      (post-process app tmp-dest)
      (apps-db/assoc-field! db app-key :version latest-version))
    (log/infof "App %s is at the latest version: %s"
               app-name
               current-version)))

(defprotocol Updater
  "File updater protocol."

  (start-update! [_]
    "Start a single round of updates."))

(defrecord SingleRunUpdater [db sources]
  Updater
  (start-update!
    [_]
    (doseq [[app-key app] (seq (apps-db/get-all-apps db))
            :let [source         (source-of-type sources (:source app))
                  latest-version (app-source/fetch-latest-version! source app)]]
      (update-file db source (assoc app :app-key app-key) latest-version))))

(defmethod ig/init-key ::single-run-updater
  [_ {:keys [db sources], :as config}]
  (let [source-types (mapv u/->str sources)]
    (log/infof "Single run updater: db=<%s> sources=%s" (u/->str db) (pr-str source-types)))
  (map->SingleRunUpdater config))
