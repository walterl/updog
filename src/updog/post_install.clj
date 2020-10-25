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
  [{:keys [app-key installed shell-script] :as app} _]
  (when shell-script
    (let [script-args (select-keys app [:downloaded :install-file :installed])]
      (doseq [script (if (sequential? shell-script) shell-script [shell-script])]
        (log/debug ::shell-script {:app-key      app-key
                                   :shell-script script
                                   :args         script-args})
        (u/shell-script script script-args))))
  installed)

(defn post-install-app!
  "Applies all `:post-install` actions to `app` and returns a vector of
  results."
  [{:keys [post-install] :as app}]
  (mapv (partial post-install-action app) post-install))
