(ns updog.updater
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [integrant.core :as ig]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :as fs.comp]
            [taoensso.timbre :as log]
            [updog.apps-db :as apps-db]
            [updog.app-source :as app-source]))

(defn- download-file!
  [url dest]
  (with-open [out (io/output-stream dest)]
    (io/copy (:body (http/get url {:as :stream}))
             out)))

(defn- exec!
  [filename dir command]
  ;; TODO Test this
  (sh (-> command
             (str/replace #"APPFILE" filename)
             (str/split #"\s+"))
      :dir dir))

(defn- newer-version?
  "Is version `a` newer than `b`?"
  [a b]
  (pos? (compare a b)))

(defn- post-process
  [app-data]
  ;; TODO Test this
  (let [{:keys [local-path post-proc]} app-data
        parent-dir                     (fs/parent local-path)]
    (cond
      (= :unzip post-proc) (fs.comp/unzip local-path parent-dir)
      (string? post-proc)  (exec! local-path parent-dir post-proc))))

(defn- source-of-type
  [sources source-type]
  (some #(when (= source-type (app-source/source-type %))
           %)
        sources))

(defn- update-file
  [db
   {current-version :version
    app-key         :app-key
    app-name        :name
    local-path      :local-path
    :as app-data}
   {latest-version :version
    :keys [download-url]}]
  (if (newer-version? latest-version current-version)
    (do
      (log/infof "App %s/%s (%s) has newer version: %s"
                 app-name
                 app-key
                 current-version
                 latest-version)
      (download-file! download-url local-path)
      (apps-db/assoc-field! db app-key :version latest-version)
      (post-process app-data))
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
    (apps-db/initialize! db)
    (doseq [[app-key app-data] (seq (apps-db/get-all-apps db))
            :let [source         (source-of-type sources (:source app-data))
                  latest-version (app-source/fetch-latest-version! source app-data)]]
      (update-file db (assoc app-data :app-key app-key) latest-version))))

(defmethod ig/init-key ::single-run-updater
  [_ config]
  (log/info "Single run updater:" config)
  (map->SingleRunUpdater config))
