(ns updog.app-source.local-zip
  (:require [integrant.core :as ig]
            [me.raynes.fs :as fs]
            [taoensso.timbre :as log]
            [updog.app-source :refer [AppSource source-type]]
            [updog.util :as u]))

(defrecord LocalZipSource []
  AppSource
  (source-type [_] :local-zip)

  (fetch-latest-version-data!
    [_ {:keys [local-zip tmp-dir] :as app}]
    (log/debug ::fetch-latest-version-data!
               (select-keys app [:local-zip :name :tmp-dir]))
    (try
      (let [bin-path (first (u/unzipped-files local-zip tmp-dir))]
        (u/chmod+x bin-path)
        {:version  (u/cmd-version bin-path)
         :bin-path bin-path
         :filename (last (fs/split local-zip))
         :location local-zip})
      (catch java.nio.file.NoSuchFileException _
        (log/error "Local zip file not found:" local-zip)
        {:error ::file-not-found})))

  (download!
    [_ {:keys [app-key bin-path tmp-dir], {:keys [location]} :latest-version}]
    (if (fs/exists? bin-path)
      (do
        (log/debug ::download! {:app-key app-key
                                :src     bin-path
                                :dest    bin-path
                                :status  ::already-exists})
        bin-path)
      (do
        (log/debug ::download! {:app-key app-key
                                :src     location
                                :dest    :dest
                                :status  ::unzip})
        (first (u/unzipped-files location tmp-dir))))))

(defmethod u/->str LocalZipSource
  [src]
  (name (source-type src)))

(defmethod ig/init-key ::app-source
  [_ config]
  (log/debug "Initializing app source: local zip")
  (map->LocalZipSource config))
