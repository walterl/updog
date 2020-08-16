(ns updog.post-install
  (:require [taoensso.timbre :as log]
            [updog.util :as u]))

(defmulti post-install-action
  "Action to take after installation, dispatched on `action`.
  Installed binary is at `(:installed _app)`."
  (fn [_app action] action))

(defmethod post-install-action :default
  [app action]
  (throw (ex-info "Unknown post-install action:" {:action action, :app app})))

(defmethod post-install-action :chmod+x
  [{:keys [installed] :as app} _]
  (log/debug ::chmod+x (select-keys app [:installed :name]))
  (u/chmod+x installed)
  installed)

(defmethod post-install-action :shell-script
  [{:keys [app-key shell-script] :as app} _]
  (let [args (select-keys app [:downloaded :install-file :installed])]
    (log/debug ::shell-script {:app-key      app-key
                               :shell-script shell-script
                               :args         args})
    (u/shell-script shell-script args)))

(defn post-install-app!
  "Applies all `:post-install` actions to `app` and returns a vector of
  results."
  [{:keys [post-install] :as app}]
  (mapv (partial post-install-action app) post-install))
