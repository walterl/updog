(ns updog.fs
  (:require
    [clojure.string :as str]
    [me.raynes.fs :as fs])
  (:import
    [java.io File]))

(def chmod fs/chmod)
(def copy fs/copy+)
(def delete fs/delete)
(def exists? fs/exists?)
(def executable? fs/executable?)
(def extension (fnil fs/extension ""))
(def parent (comp str fs/parent))

(def ^:private invalid-dir-name "__INVALID_DIR_NAME__")

(def writeable-dir?
  (every-pred fs/writeable? (fnil fs/directory? invalid-dir-name)))

(defn chmod-files
  "Apply `perm`issions to all `files`."
  [files perm]
  (when (and perm files)
    (doseq [filename files]
      (chmod perm filename))))

(defn expand-home
  [d]
  (some-> d
          (fs/expand-home)
          (str)))

(defn file-name
  [x]
  (when (not-empty x)
    (not-empty (fs/base-name x))))

(defn path
  "Joins `args` with file separator."
  [& args]
  (not-empty (str/join File/separator args)))

(defn exec-paths
  []
  (.split (System/getenv "PATH") File/pathSeparator))

(def ^:dynamic *sys-path-dirs* nil)

(defn sys-path-dirs
  "Returns vector of `$PATH`."
  []
  (or *sys-path-dirs* (exec-paths)))

(def temp-dir
  "Returns the current process's temporary directory."
  (memoize
    (fn temp-dir [& [suffix]]
      (fs/temp-dir "updog-" (if suffix (str "-" suffix) "")))))

(defn ensure-dir!
  "Create directory (and parent(s)) if it doesn't exist."
  [dir]
  (when-not (fs/directory? dir)
    (fs/mkdirs dir)))

(def PERM_R 4)
(def PERM_W 2)
(def PERM_X 1)

(defn unpack-perms
  "Unpack user, group and \"other\" permissions from given UNIX `mode`."
  [mode]
  {:user (mod (int (/ mode (* 8 8))) 8)
   :group (mod (int (/ mode 8)) 8)
   :other (mod mode 8)})
