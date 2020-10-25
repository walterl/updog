(ns updog.updater
  (:require [integrant.core :as ig]
            [me.raynes.fs :as fs]
            [taoensso.timbre :as log]
            [updog.apps-db :as apps-db]
            [updog.app-source :as app-source]
            [updog.post-install :as post-install]
            [updog.unpack :as unpack]
            [updog.util :as u]))

(defn- newer-version?
  "Is version `a` newer than `b`? Always returns `true` if `b` is nil."
  [a b]
  (or (nil? b)
      (pos? (compare a b))))

(defn- has-latest-version?
  [{:keys [latest-version]}]
  (and latest-version (:version latest-version)))

(defn- source-of-type
  [sources source-type]
  (or (first (filter #(= source-type (app-source/source-type %))
                     sources))
      (throw (ex-info "Source not found!" {:source-type source-type
                                           :sources     sources}))))

(defn- should-update?
  [{:keys [dest-path]
    app-name                  :name
    {last-version :version}   :version
    {latest-version :version} :latest-version}]
  (cond
    (not (fs/exists? dest-path))
    (do
      (log/debugf "File not found: %s" dest-path)
      true)

    (newer-version? latest-version last-version)
    (do
      (log/debugf "App %s %s has newer version: %s"
                  app-name last-version latest-version)
      true)

    :else
    false))

(defn- init-update
  [{:keys [app-key latest-version tmp-dir], app-name :name, :as app}]
  (log/infof "Updating app %s to version %s..." app-name (:version latest-version))
  (let [app-tmp-dir (str tmp-dir "/" (name app-key))]
    (fs/mkdirs app-tmp-dir)
    (assoc app
           :base-tmp-dir tmp-dir
           :tmp-dir      app-tmp-dir)))

(defn- source-dispatch
  [{:keys [source] :as app} method]
  (method source app))

(defn- install!
  [{:keys [dest-path install-file]}]
  (u/copy! install-file dest-path)
  dest-path)

(defn- update-db!
  [{:keys [app-key db latest-version], app-name :name}]
  (apps-db/assoc-field! db app-key :version latest-version)
  (apps-db/assoc-field! db app-key :last-updated-at (java.util.Date.))
  (log/infof "App %s updated to version %s" app-name latest-version))

(defn- update-file
  [{:keys [source], app-name :name, {last-version :version} :version, :as app}]
  (let [app (-> app
                (assoc :latest-version (app-source/fetch-latest-version-data! source app))
                init-update)]
    (when-not (has-latest-version? app)
      (throw (ex-info (format "Unable to find latest version for %s" app-name)
                      {:type ::no-latest-version
                       :app  app})))
    (if (should-update? app)
      (u/assoc-f app
                 :downloaded   #(source-dispatch % app-source/download!)
                 :install-file unpack/unpack-app!
                 :installed    install!
                 :post-install post-install/post-install-app!
                 :db-updated   update-db!)
      (log/infof "App %s is at the latest version: %s" app-name last-version))))

(defprotocol Updater
  "File updater protocol."

  (start-update! [_ selected-apps]
    "Start a single round of updates."))

(defrecord SingleRunUpdater [db sources]
  Updater
  (start-update!
    [_ selected-apps]
    (let [selected-apps (set (map keyword selected-apps))
          base-tmp-dir  (u/tmp-dir)]
      (doseq [[app-key app] (seq (apps-db/get-all-apps db))
              :when (or (empty? selected-apps)
                        (selected-apps app-key))]
        (try
          (update-file (assoc app
                              :app-key        app-key
                              :tmp-dir        base-tmp-dir
                              :db             db
                              :source         (source-of-type sources (:source-type app))))
          (catch Throwable ex
            (log/errorf ex "Failed to update app %s" app-key)))))))

(defmethod ig/init-key ::single-run-updater
  [_ {:keys [db sources], :as config}]
  (let [source-types (mapv u/->str sources)]
    (log/debugf "Single run updater: db=<%s> sources=%s" (u/->str db) (pr-str source-types)))
  (map->SingleRunUpdater config))
