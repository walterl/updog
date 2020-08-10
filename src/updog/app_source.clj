(ns updog.app-source
  (:require [updog.util :as u]))

(defprotocol AppSource
  "App sources define how to get the current version of an app."

  (source-type [_]
    "Should return a constant keyword identifying the source type.")

  (fetch-latest-version-data! [_ app]
    "Fetch the latest version of the `app` with the given data.

    Return value will be `assoc`ed under `:latest-version` key of `app`, and
    should contain whatever data `download!` needs to download the the latest
    version.")

  (download! [_ app]
    "Download the file for the latest version of `app`. Must return `app`, with
    `:install-file` value - the file to be installed - `assoc`ed in."))

(defmethod u/->str AppSource
  [src]
  (name (source-type src)))
