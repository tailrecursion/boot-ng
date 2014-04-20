(ns tailrecursion.boot
  (:require
   [clojure.java.io                     :as io]
   [tailrecursion.boot.core             :as core]
   [tailrecursion.boot.core.util        :as util])
  (:gen-class))

(defn -main [& args]
  (println "howdy")
  (prn @core/dependencies)
  (System/exit 0))
