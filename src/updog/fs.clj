(ns updog.fs
  (:require
    [clojure.string :as str]
    [me.raynes.fs :as fs])
  (:import
    [java.io File]
    [java.net URI]
    [java.nio.file Files Paths]
    [java.nio.file.attribute PosixFilePermission]))

(def copy fs/copy+)
(def delete fs/delete)
(def exists? fs/exists?)
(def executable? fs/executable?)
(def extension (fnil fs/extension ""))
(def parent (comp str fs/parent))

(def ^:private invalid-dir-name "__INVALID_DIR_NAME__")

(def writeable-dir?
  (every-pred fs/writeable? (fnil fs/directory? invalid-dir-name)))

(defn- expand-perms
  [iperms]
  (filterv
    some?
    [(when (pos-int? (bit-and (int (/ iperms (* 8 8))) 04))
       PosixFilePermission/OWNER_READ)
     (when (pos-int? (bit-and (int (/ iperms (* 8 8))) 02))
       PosixFilePermission/OWNER_WRITE)
     (when (pos-int? (bit-and (int (/ iperms (* 8 8))) 01))
       PosixFilePermission/OWNER_EXECUTE)
     (when (pos-int? (bit-and (int (/ iperms 8)) 04))
       PosixFilePermission/GROUP_READ)
     (when (pos-int? (bit-and (int (/ iperms 8)) 02))
       PosixFilePermission/GROUP_WRITE)
     (when (pos-int? (bit-and (int (/ iperms 8)) 01))
       PosixFilePermission/GROUP_EXECUTE)
     (when (pos-int? (bit-and iperms 04))
       PosixFilePermission/OTHERS_READ)
     (when (pos-int? (bit-and iperms 02))
       PosixFilePermission/OTHERS_WRITE)
     (when (pos-int? (bit-and iperms 01))
       PosixFilePermission/OTHERS_EXECUTE)]))

(defn chmod-files
  "Apply octal `perm`issions to all `files`."
  [files perm]
  (when (and perm (not-empty files))
    (doseq [filename files]
      (Files/setPosixFilePermissions
        (Paths/get (URI/create (str "file://" filename)))
        (set (expand-perms perm))))))

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
