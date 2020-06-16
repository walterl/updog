(ns updog.updater
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log]
            [updog.apps-db :as apps-db]
            [updog.app-source :as app-source]))

(defn- newer-version?
  "Is version `a` newer than `b`?"
  [a b]
  (pos? (compare a b)))

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
            :let [{current-version :version
                   app-name        :name
                   app-source      :source} app-data
                  source                    (some #(when (= app-source (app-source/source-type %))
                                                     %)
                                                  sources)
                  latest-version            (app-source/fetch-latest-version! source app-data)]]
      (if (newer-version? (:version latest-version) current-version)
        (log/infof "App %s/%s (%s) has newer version: %s"
                   app-name
                   app-key
                   current-version
                   latest-version)
        (log/infof "App %s is at the latest version: %s"
                   app-name
                   current-version)))))

(defmethod ig/init-key ::single-run-updater
  [_ config]
  (log/info "Single run updater:" config)
  (map->SingleRunUpdater config))
