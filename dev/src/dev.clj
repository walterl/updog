(ns dev
  (:require
    [clojure.spec.alpha :as s]
    [expound.alpha :as expound]
    [lambdaisland.classpath.watch-deps :as watch-deps]
    [pjstadig.humane-test-output :as humane]
    [updog.main :as main]))

(main/init-logging! :debug)
(watch-deps/start! {:aliases [:dev :test]})
(humane/activate!)
#_(alter-var-root #'s/*explain-out* (constantly s/explain-printer))
(alter-var-root #'s/*explain-out* (constantly expound/printer))

(comment
  ;;; Scratchpad
  ,)
