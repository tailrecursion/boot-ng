(ns tailrecursion.boot
  (:require
   [clojure.java.io           :as io]
   [clojure.tools.cli         :as cli]
   [clojure.stacktrace        :as trace]
   [clojure.pprint            :as pprint]
   [tailrecursion.maven       :as maven]
   [tailrecursion.classloader :as loader])
  (:import
   [java.io File]
   [java.util.jar JarFile]
   [org.springframework.util AntPathMatcher])
  (:gen-class))

(defmacro guard [expr]
  `(try ~expr (catch Throwable e#)))

(defn glob-match?
  [pattern path]
  (.match (AntPathMatcher.) pattern path))

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
  (let [repl (loader/make-repl-server)
        pod  (loader/make-pod repl)]
    (prn ((loader/make-pod-client pod) '(require '[tailrecursion.boot.core :refer :all])))
    (prn ((loader/make-pod-client pod) '(+ 1 2 3)))
    (prn ((loader/make-pod-client pod) '(+ 1 2 3 4)))
    (prn ((loader/make-pod-client pod) '(+ 1 2 3 4 5)))
    (prn ((loader/make-pod-client pod) '(pr-str get-env)))
    ))

#_(defn -main [& [arg0 & args :as args*]]
    (try
      (reset! maven/offline? true)
      (let [core-dep*          (guard (cli-opts! (core-dep)))
            dotboot?           #(.endsWith (.getName (io/file %)) ".boot")
            script?            #(when (and % (.isFile (io/file %)) (dotboot? %)) %)
            [errs opts args**] (when core-dep* (parse-opts (if (script? arg0) args args*)))
            args               (concat (if (script? arg0) [arg0] []) args**)]
        (when (and (seq errs) core-dep*)
          (binding [*out* *err*]
            (println (apply str (interpose "\n" errs)))
            (System/exit 1)))
        (when (:freshen opts) (reset! maven/update? :always))
        (when (:no-freshen opts) (reset! maven/update? :never))
        (reset! maven/offline? (:offline opts))
        (let [fetch?   (and (not= :never @maven/update?) (not @maven/offline?))
              clj-url  (map :jar (clj-dep))
              core-url (map :jar (if fetch? (core-dep) core-dep*))
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
