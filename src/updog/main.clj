(ns updog.main
  (:require
    [updog.app :as app]
    [updog.config :as config])
  (:gen-class))

(defn update-apps!
  [{:keys [:updog/defaults] :as config}]
  (for [[app-key app-config] config
        :when (not= :updog/defaults app-key)]
    {::key app-key
     ::config config
     ::result (-> (merge defaults app-config)
                  (config/app-prep app-key)
                  (app/process))}))

(defn -main
  [config-fname]
  (update-apps! (config/read config-fname)))

(comment
  (update-apps! {:updog/defaults
                 {:install-dir "~/.local/bin/"
                  :archive-dir "~/tmp"}
                 :clj-kondo/clj-kondo
                 {:asset "linux-static-amd64"}})
  (Throwable->map *e)
  )
