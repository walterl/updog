(ns updog.unpack
  (:require [taoensso.timbre :as log]
            [updog.util :as u]))

(defmulti unpack-action
  "Unpack downloaded app, dispatched on `action`."
  (fn [_app action] action))

(defmethod unpack-action :default
  [_app action]
  (log/errorf "Unknown unpack action:" action))

(defmethod unpack-action :no-unpack
  [{:keys [downloaded]} _]
  downloaded)

(defmethod unpack-action :unzip
  [{:keys [downloaded tmp-dir]} _]
  (first (u/unzipped-files downloaded tmp-dir "unpack-")))

(defn unpack-app!
  "Performs all `:unpack` actions on `app` and returns a vector of results."
  [{:keys [unpack] :as app}]
  (some (partial unpack-action app)
        (or (not-empty unpack) [:no-unpack])))
