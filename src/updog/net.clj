(ns updog.net
  (:require
    [clojure.java.io :as io]
    [cheshire.core :as json]
    [clj-http.client :as http]))

(defn download-file
  "Download `url` to `dest`; returns `dest`.

  Creates temp file if `dest` was not provided."
  [url dest]
  (with-open [out (io/output-stream dest)]
    (io/copy (:body (http/get url {:as :stream}))
             out))
  dest)

(defn fetch-json
  [url]
  (-> (http/get url)
      :body
      (json/parse-string true)))
