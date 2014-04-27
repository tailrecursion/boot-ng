(ns tailrecursion.boot.core.classloader
  (:require
   [clojure.java.io                     :as io]
   [tailrecursion.boot.core.classlojure :as cl]
   [tailrecursion.boot.core.env         :as env]
   [tailrecursion.boot.core.util        :as util])
  (:import
   [java.net URLClassLoader URL URI]
   java.lang.management.ManagementFactory))

(defn make-classloader []
  (let [path (-> "boot-classloader-resource-path" io/resource slurp .trim)
        jar  (io/file env/+boot-dir+ path)]
    (when (.createNewFile jar) (util/copy-resource path jar))
    (let [cl (cl/classlojure (str "file:" (.getPath jar)))] #(cl/eval-in cl %))))

(def eval-in-cl2  (let [cl (future (make-classloader))] #(@cl %)))
(def dep-jars     (atom []))
(def dependencies (atom (or (:dependencies (util/get-project 'tailrecursion/boot-core)) [])))
(def dfl-env      {:repositories #{"http://clojars.org/repo/" "http://repo1.maven.org/maven2/"}})

(defn prep-env [env]
  (-> (merge dfl-env env)
    (select-keys #{:dependencies :repositories :local-repo
                   :offline? :mirrors :proxy :transfer-listener})))

(defn exclude [syms coordinate]
  (if-let [idx (util/index-of coordinate :exclusions)]
    (let [exclusions (get coordinate (inc idx))]
      (assoc coordinate (inc idx) (into exclusions syms)))
    (into coordinate [:exclusions syms])))

(defn add-dirs! [dirs]
  (when (seq dirs)
    (let [meth  (doto (.getDeclaredMethod URLClassLoader "addURL" (into-array Class [URL]))
                  (.setAccessible true))
          cldr  (ClassLoader/getSystemClassLoader)
          dirs  (->> dirs (map io/file) (filter #(.exists %)) (map #(.. % toURI toURL)))]
      (doseq [url dirs] (.invoke meth cldr (object-array [url]))))))

(defn resolve-deps! [env]
  (let [env (prep-env env)]
    (eval-in-cl2
      `(do (require 'tailrecursion.boot-classloader)
           (tailrecursion.boot-classloader/resolve-dependencies! '~env)))))

(defn add-deps! [env]
  (let [{deps :dependencies :as env} (prep-env env)
        loaded (->> @dependencies (map first) set)
        specs  (->> deps
                 (remove (comp (partial contains? loaded) first))
                 (mapv (partial exclude (vec loaded)))
                 (assoc env :dependencies)
                 resolve-deps!)]
    (add-dirs! (map #(URL. (str "file://" (:jar %))) specs))
    (swap! dep-jars into (map :jar specs))
    (swap! dependencies into (map :dep specs))))

(defn glob-match? [pattern path]
  (eval-in-cl2
    `(do (require 'tailrecursion.boot-classloader)
         (tailrecursion.boot-classloader/glob-match? ~pattern ~path))))

(defn parse-opts [& args]
  (eval-in-cl2
    `(do (require 'clojure.tools.cli)
         (clojure.tools.cli/parse-opts ~@args))))

(defn make-pod [& {:keys [src-paths] :as env}]
  (let [env   (prep-env env)
        clj?  #(= 'org.clojure/clojure (first (:dep %)))
        {[{clj-jar :jar}] true, other-deps false}
        (->> env resolve-deps! (group-by clj?))]
    (when-not (and clj-jar (.exists (io/file clj-jar)))
      (throw (Exception. "Pod has no Clojure dependency")))
    (let [clj-path (.getPath (io/file clj-jar))
          pod      (cl/classlojure (str "file:" clj-path))
          deps     (->> other-deps (mapv :jar) (into src-paths))
          files    (->> deps (mapv io/file) (filter #(.exists %)))]
      (doseq [file files] (.addURL pod (.. file toURI toURL)))
      (fn [expr] (cl/eval-in pod expr)))))
