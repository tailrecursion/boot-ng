(ns tailrecursion.boot-podloader
  (:require
   [clojure.java.io :as io])
  (:import
   [java.net URLClassLoader URL]
   java.lang.management.ManagementFactory)
  (:gen-class))

(defn add-dirs!
  [dirs]
  (when (seq dirs)
    (let [meth  (doto (.getDeclaredMethod URLClassLoader "addURL" (into-array Class [URL]))
                  (.setAccessible true))
          cldr  (ClassLoader/getSystemClassLoader)
          dirs  (->> dirs (map io/file) (filter #(.exists %)) (map #(.. % toURI toURL)))]
      (doseq [url dirs] (.invoke meth cldr (object-array [url]))))))

(defn add-jars!
  [jar-paths]
  (add-dirs! (map #(URL. (str "file://" %)) jar-paths)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
