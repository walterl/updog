(ns updog.fs 
  (:require [babashka.fs :as fs]))

(def ^:private invalid-dir-name "__INVALID_DIR_NAME__")

(def exists? (fnil fs/exists? invalid-dir-name))
(def file-name (-> (comp not-empty str fs/file-name)
                   (fnil "")))
(def path (-> (comp not-empty str fs/path)
              (fnil "")))
(def writable-dir? (-> (every-pred fs/writable? fs/directory?)
                       (fnil invalid-dir-name)))

(defn expand-home
  [d]
  (some-> d
          (fs/expand-home)
          (str)))

(def ^:dynamic *sys-path-dirs* nil)

(defn sys-path-dirs
  []
  (or *sys-path-dirs*
      (map str (fs/exec-paths))))
