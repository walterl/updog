(ns updog.main
  (:require
    [clojure.pprint :refer [pprint]]
    [updog.app :as app]
    [updog.config :as config])
  (:gen-class))

(defn update-apps!
  [{:updog/keys [defaults] :as config}]
  (for [[app-key app-config] config
        :when (not= :updog/defaults app-key)
        :let [config (-> (merge defaults app-config)
                         (config/app-prep app-key))]]
    {::key app-key
     ::config config
     ::result (app/process config)}))

(defn -main
  [config-fname]
  (pprint (update-apps! (config/read config-fname))))

(comment
  (def config-fname "test-config.edn")
  (def config (config/read config-fname))
  (def defaults (:updog/defaults config))
  (def app-key :clj-kondo/clj-kondo)
  (def app-key :clojure-lsp/clojure-lsp)
  (def app-config (get config app-key))
  (def prepped-config (-> (merge defaults app-config)
                          (config/app-prep app-key)))

  (update-apps! {:updog/defaults
                 {:install-dir "~/.local/bin/"
                  :archive-dir "~/tmp"}
                 :clj-kondo/clj-kondo
                 {:asset "linux-static-amd64"}})
  (Throwable->map *e)
  )
