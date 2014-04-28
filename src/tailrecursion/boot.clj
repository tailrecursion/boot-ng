(ns tailrecursion.boot
  (:require
   [clojure.java.io                :as io]
   [classlojure.core               :as cl]
   [clojure.tools.cli              :as cli]
   [clojure.stacktrace             :as trace]
   [clojure.pprint                 :as pprint]
   [tailrecursion.boot-classloader :as loader])
  (:import
   [java.net URL URLClassLoader]
   [clojure.lang DynamicClassLoader])
  (:gen-class))

(defn url-str [deps]
  (->> deps first :jar io/file .getPath (str "file:")))

(def clojars       {:url "http://clojars.org/repo/"})
(def maven-central {:url "http://repo1.maven.org/maven2/"})

(defn clj-dep []
  (loader/resolve-dependencies!
    {:repositories #{maven-central}
     :dependencies '[[org.clojure/clojure "1.5.1"]]}))

(defn core-dep []
  (loader/resolve-dependencies!
    {:repositories #{clojars}
     :dependencies '[[tailrecursion/boot-core "2.0.0-SNAPSHOT"]]}))

(defn- dyn-classloader [urls ext]
  (let [loader (DynamicClassLoader. ext)
        urls   (map io/as-url (flatten urls))]
    (doseq [u urls] (.addURL loader u))
    loader))

(defn make-cl [& urls]
  (let [^URLClassLoader cl (dyn-classloader urls cl/ext-classloader)]
    (.loadClass cl "clojure.lang.RT")
    (cl/eval-in* cl '(require 'clojure.main))
    cl))

(defn get-resources [name]
  (->> (.. Thread currentThread getContextClassLoader (getResources name))
    enumeration-seq))

(defn get-project [sym]
  (when-let [pform (->> (get-resources "project.clj") 
                     (map (comp read-string slurp))
                     (filter (comp (partial = sym) second))
                     first)]
    (->> pform (drop 1) (partition 2) (map vec) (into {}))))

(defn boot-version []
  (-> 'tailrecursion/boot get-project (get 'tailrecursion/boot)))

(defn parse-opts [args]
  (let [opts [["-U" "--update"]
              ["-o" "--offline"]
              ["-P" "--no-profile"]
              ["-h" "--help"]
              ["-V" "--version"]]]
    ((juxt :errors :options :arguments)
     (cli/parse-opts args opts :in-order true))))

(defn -main [& [arg0 & args :as args*]]
  (try
    (let [dotboot?   #(.endsWith (.getName (io/file %)) ".boot")
          script?    #(when (and % (.isFile (io/file %)) (dotboot? %)) %)
          args       (if (script? arg0) args args*)
          [_ opts _] (parse-opts args)]
      (when (:update opts) (reset! loader/update? true))
      (when (:offline opts) (reset! loader/offline? true))
      (let [clj-url  (url-str (clj-dep))
            core-url (url-str (core-dep))
            core-pod (make-cl clj-url core-url)]
        (cl/eval-in core-pod
          `(do (require 'tailrecursion.boot)
               (tailrecursion.boot/-main ~(boot-version) ~@args*))))
      (flush)
      (System/exit 0))
    (catch Throwable e
      (binding [*out* *err*]
        (trace/print-cause-trace e)
        (flush)
        (System/exit 1)))))
