(ns updog.app-source)

(defprotocol AppSource
  "App sources define how to get the current version of an app."

  (source-type [_]
    "Should return a constant keyword identifying the source type.")

  (fetch-latest-version! [_ app]
    "Fetch the latest version of the `app` with the given data."))
