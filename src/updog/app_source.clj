(ns updog.app-source
  (:require [updog.util :as u]))

(defprotocol AppSource
  "App sources define how to get the current version of an app."

  (source-type [_]
    "Should return a constant keyword identifying the source type.")

  (fetch-latest-version! [_ app]
    "Fetch the latest version of the `app` with the given data.")

  (download! [_ version-info dest-path]
    "Download the file from `(:location version-info)` to `dest-path`."))

(defmethod u/->str AppSource
  [src]
  (name (source-type src)))
