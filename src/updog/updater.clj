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
  "Is version `a` newer than `b`? Always returns `true` if `b` is nil."
  [a b]
  (or (nil? b)
      (pos? (compare a b))))

(defmulti post-process
  "Dispatches on the first argument, which whould be a value from the app's
  `:post-proc` key."
  (fn [post-proc _app _downloaded-path]
    post-proc))

(defmethod post-process :default
  [k _ _]
  (log/warnf "Don't know how to handle post-processing key: %s" k))

(defmethod post-process :copy
  [_ {:keys [dest-path]} downloaded-path]
  (u/copy! downloaded-path dest-path))

(defmethod post-process :chmod+x
  [_ {:keys [dest-path]} _downloaded-path]
  (log/debugf "chmod u+x %s" dest-path)
  (fs/chmod "u+x" dest-path))

(defmethod post-process :shell-script
  [_ {:keys [dest-path shell-script], :as app} downloaded-path]
  (when-not shell-script
    (throw (ex-info "Shell script missing" {:type ::missing-shell-script
                                            :app  app})))
  (let [sh-vars {:dl-file   downloaded-path
                 :dest-file dest-path
                 :dest-dir  (str (fs/parent dest-path))}]
    (log/debugf "shell: %s %s" shell-script sh-vars)
    (apply sh (u/command->sh-args shell-script sh-vars))))

(defmethod post-process :unzip
  [_ {:keys [dest-path]} downloaded-path]
  (let [dest-dir (fs/parent dest-path)]
    (log/debugf "unzip %s %s" downloaded-path dest-dir)
    (fs.comp/unzip downloaded-path dest-dir)))

(defn- source-of-type
  [sources source-type]
  (or (first (filter #(= source-type (app-source/source-type %))
                     sources))
      (throw (ex-info "Source not found!" {:source-type source-type
                                           :sources     sources}))))

(defn- should-update?
  [{:keys [dest-path version], app-name :name} {latest-version :version}]
  (cond
    (not (u/file-exists? dest-path))
    (do
      (log/debugf "File not found: %s" dest-path)
      true)

    (newer-version? latest-version version)
    (do
      (log/debugf "App %s %s has newer version: %s"
                  app-name version latest-version)
      true)

    :else
    false))

(defn- update-file
  [db src
   {:keys [app-key], current-version :version, app-name :name, :as app}
   {latest-version :version, :as latest-version-info}]
  (if (should-update? app latest-version-info)
    (let [tmp-dest (u/temp-file-path)]
      (log/infof "Updating app %s to version %s..." app-name latest-version)
      (app-source/download! src latest-version-info tmp-dest)
      (doseq [k (get app :post-proc [])]
        (try
          (post-process k app tmp-dest)
          (catch Throwable ex
            (log/errorf ex "App %s post-proc %s failed" app-name k))))
      (apps-db/assoc-field! db app-key :version latest-version)
      (log/infof "App %s updated to version %s" app-name latest-version))
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
      (try
        (update-file db source (assoc app :app-key app-key) latest-version)
        (catch Throwable ex
          (log/errorf ex "Failed to update app %s" app-key))))))

(defmethod ig/init-key ::single-run-updater
  [_ {:keys [db sources], :as config}]
  (let [source-types (mapv u/->str sources)]
    (log/infof "Single run updater: db=<%s> sources=%s" (u/->str db) (pr-str source-types)))
  (map->SingleRunUpdater config))
