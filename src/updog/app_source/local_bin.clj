(ns updog.app-source.local-bin
  (:require [integrant.core :as ig]
            [me.raynes.fs :as fs]
            [taoensso.timbre :as log]
            [updog.app-source :refer [AppSource source-type]]
            [updog.util :as u]))

(defrecord LocalBinSource []
  AppSource
  (source-type [_] :local-bin)

  (fetch-latest-version-data!
    [_ {:keys [local-bin], :as app}]
    (log/debug ::fetch-latest-version-data-data!
               (select-keys app [:local-bin :name]))
    {:version  (u/cmd-version local-bin)
     :filename (last (fs/split local-bin))
     :location local-bin})

  (download!
    [_ {:keys [app-key tmp-dir]
        {:keys [location filename]} :latest-version
        :as app}]
    (when-not (fs/exists? location)
      (throw (ex-info "Local file does not exist" {:type     ::local-file-missing
                                                   :filename location})))
    (let [tmp-path (str tmp-dir "/" filename)]
      (log/debug ::download! {:app-key app-key
                              :src     location
                              :dest    tmp-path})
      (u/copy! location tmp-path)
      tmp-path)))

(defmethod u/->str LocalBinSource
  [src]
  (name (source-type src)))

(defmethod ig/init-key ::app-source
  [_ config]
  (log/debug "Initializing app source: local bin")
  (map->LocalBinSource config))
