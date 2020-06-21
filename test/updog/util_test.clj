(ns updog.util-test
  (:require [clojure.test :refer :all]
            [updog.util :as u]))

(deftest command->args-tests
  (is (= ["ls"]
         (u/command->args "ls")))
  (is (= ["ls" "-l"]
         (u/command->args "ls -l")))
  (is (= ["ls" "-l" "/home/walter"]
         (u/command->args "ls -l HOME_DIR" {:home-dir "/home/walter"}))))
