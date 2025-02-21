(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'net.clojars.walterl/updog)
(def version (format "0.3.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean
  [_]
  (println "Deleting 'target' dir...")
  (b/delete {:path "target"})
  (println "Done ✅"))

(defn uber
  [_]
  (clean nil)
  (printf "Preparing target dir (%s)...\n" class-dir)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (println "Compiling...")
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (println (format "Building JAR at %s..." uber-file))
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'updog.main})
  (println "Done ✅"))
