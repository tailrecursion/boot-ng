(ns tailrecursion.boot
  (:require
   [clojure.java.io                     :as io]
   [tailrecursion.boot.core.classlojure :as cl]
   [clojure.tools.cli                   :as cli]
   [clojure.stacktrace                  :as trace]
   [clojure.pprint                      :as pprint]
   [tailrecursion.boot-classloader      :as loader])
  (:import
   [java.io File]
   [java.net URL URLClassLoader]
   [java.util.jar JarFile]
   [clojure.lang DynamicClassLoader])
  (:gen-class))

(defmacro guard [expr]
  `(try ~expr (catch Throwable e#)))

(defn slurp-entry [jar-path entry-path]
  (let [jar (JarFile. jar-path)]
    (when-let [entry (.getEntry jar entry-path)]
      (-> jar (.getInputStream entry) slurp))))

(defn cli-opts! [deps]
  (let [jar (-> deps first :jar)
        cli "tailrecursion/boot/core/cli_opts.clj"]
    (when-let [src (slurp-entry jar cli)]
      (let [f (doto (File/createTempFile "cli_opts" ".clj") .deleteOnExit)
            p (.getPath f)]
        (doto p (spit src) load-file)))
    deps))

(defn url-str [deps]
  (for [dep deps] (->> dep :jar io/file .getPath (str "file:"))))

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
  (require 'tailrecursion.boot.core.cli-opts)
  (when-let [opts (resolve 'tailrecursion.boot.core.cli-opts/opts)]
    ((juxt :errors :options :arguments)
     (cli/parse-opts args (var-get opts) :in-order true))))

(defn -main [& [arg0 & args :as args*]]
  (try
    (reset! loader/offline? true)
    (let [core-dep*          (guard (cli-opts! (core-dep)))
          dotboot?           #(.endsWith (.getName (io/file %)) ".boot")
          script?            #(when (and % (.isFile (io/file %)) (dotboot? %)) %)
          [errs opts args**] (when core-dep* (parse-opts (if (script? arg0) args args*)))
          args               (concat (if (script? arg0) [arg0] []) args**)]
      (when (and (seq errs) core-dep*)
        (binding [*out* *err*]
          (println (apply str (interpose "\n" errs)))
          (System/exit 1)))
      (when (:freshen opts) (reset! loader/update? :always))
      (when (:no-freshen opts) (reset! loader/update? :never))
      (reset! loader/offline? (:offline opts))
      (let [fetch?   (and (not= :never @loader/update?) (not @loader/offline?))
            clj-url  (url-str (clj-dep))
            core-url (url-str (if fetch? (core-dep) core-dep*))
            core-pod (apply cl/classlojure (concat clj-url core-url))]
        (cl/eval-in core-pod
          `(do (require 'tailrecursion.boot)
               (tailrecursion.boot/-main ~(boot-version) ~opts ~@args))))
      (flush)
      (System/exit 0))
    (catch Throwable e
      (binding [*out* *err*]
        (trace/print-cause-trace e)
        (flush)
        (System/exit 1)))))
