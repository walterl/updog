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

(defn- post-process
  [app-data downloaded-path]
  ;; TODO Test this
  (let [{:keys [dest-path post-proc]} app-data
        parent-dir                    (fs/parent dest-path)]
    (cond
      (= :unzip post-proc) (fs.comp/unzip downloaded-path parent-dir)
      (string? post-proc)  (sh (u/command->sh-args post-proc {:dl-file   downloaded-path
                                                              :dest-file dest-path
                                                              :dest-dir  parent-dir}))
      :else                (fs/rename downloaded-path dest-path))))

(defn- source-of-type
  [sources source-type]
  (or (first (filter #(= source-type (app-source/source-type %))
                     sources))
      (throw (ex-info "Source not found!" {:source-type source-type
                                           :sources     sources}))))

(defn- update-file
  [db
   {current-version :version
    app-key         :app-key
    app-name        :name
    :as app-data}
   {latest-version :version
    :keys [download-url]}]
  (if (newer-version? latest-version current-version)
    (let [tmp-dest (u/temp-file)]
      (log/infof "App %s/%s (%s) has newer version: %s"
                 app-name
                 app-key
                 current-version
                 latest-version)
      (u/download-file! download-url tmp-dest)
      (post-process app-data tmp-dest)
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
    (doseq [[app-key app-data] (seq (apps-db/get-all-apps db))
            :let [source         (source-of-type sources (:source app-data))
                  latest-version (app-source/fetch-latest-version! source app-data)]]
      (update-file db (assoc app-data :app-key app-key) latest-version))))

(defmethod ig/init-key ::single-run-updater
  [_ config]
  (log/info "Single run updater:" config)
  (map->SingleRunUpdater config))
