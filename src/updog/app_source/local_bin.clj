(ns updog.app-source.local-bin
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [integrant.core :as ig]
            [taoensso.timbre :as log]
            [updog.app-source :refer [AppSource source-type]]
            [updog.util :as u]))

(defn- bin-version
  "Get bin version from running `cmd --version`."
  [cmd]
  (try
    (when (u/file-exists? cmd)
      (-> (sh cmd "--version")
          :out
          str/trim
          (str/split #"\s+")
          last))
    (catch Throwable ex
      (log/errorf ex "Failed to get version from `%s --version`" cmd)
      nil)))

(defrecord LocalBinSource []
  AppSource
  (source-type
    [_]
    :local-bin)

  (fetch-latest-version!
    [_ {:keys [local-bin], :as app}]
    (log/debug ::fetch-latest-version! app)
    {:version  (bin-version local-bin)
     :filename local-bin
     :location local-bin})

  (download!
    [_ {:keys [location]} dest-path]
    (u/copy! location dest-path)))

(defmethod u/->str LocalBinSource
  [src]
  (name (source-type src)))

(defmethod ig/init-key ::app-source
  [_ config]
  (log/debug "Initializing app source: local bin")
  (map->LocalBinSource config))
