(ns tailrecursion.boot
  (:require
   [clojure.java.io                     :as io]
   [clojure.string                      :as string]
   [clojure.stacktrace                  :as trace]
   [tailrecursion.boot.core             :as core]
   [tailrecursion.boot.core.env         :as env]
   [tailrecursion.boot.core.util        :as util]
   [tailrecursion.boot.core.classloader :as loader]
   )
  )

(defn read-cli [argv]
  (let [src (str "(" (string/join " " argv) "\n)")]
    (util/with-rethrow
      (read-string src)
      (format "Can't read command line as EDN: %s" src))))

(defn parse-cli [argv]
  (try (let [dfltsk `(((-> :default-task core/get-env resolve)))
             ->expr #(cond (seq? %) % (vector? %) (list* %) :else (list %))]
         (or (seq (map ->expr (or (seq (read-cli argv)) dfltsk))) dfltsk))
       (catch Throwable e (with-out-str (trace/print-cause-trace e)))))

(defn emit [boot? argv argv* edn-ex forms]
  `(~'(ns tailrecursion.boot.user
        (:require
         [tailrecursion.boot.util :refer :all]
         [tailrecursion.boot.core :refer :all :exclude [deftask]]))
    (defmacro ~'deftask
      [~'& ~'args]
      (list* '~'deftask* ~'args))
    (comment "profile")
    ~@forms
    (comment "command line")
    ~(if boot?
       (if edn-ex
         `(binding [~'*out* ~'*err*]
            (print ~edn-ex)
            #_(System/exit 1))
         `(core/boot ~@argv*))
       `(when-let [main# (resolve '~'-main)] (main# ~@argv)))))

(defn -main [boot-version opts & [arg0 & args :as args*]]
  (binding [*out* (util/auto-flush (loader/stdout))
            *err* (util/auto-flush (loader/stderr))]
    (util/exit-ok
      (let [dotboot?    #(.endsWith (.getName (io/file %)) ".boot")
            script?     #(when (and % (.isFile (io/file %)) (dotboot? %)) %)
            bootscript  (io/file (or (:BOOT_SCRIPT env/+env+) "build.boot"))
            userscript  (script? (io/file env/+boot-dir+ "profile.boot"))
            [arg0 args] (cond
                          (script? arg0)       [arg0 args]
                          (script? bootscript) [bootscript args*]
                          :else                [nil args*])
            boot?       (contains? #{nil bootscript} arg0)
            profile?    (not (:no-profile opts))
            cljarg      (parse-cli args)
            ex          (when (string? cljarg) cljarg)
            args*       (when-not (string? cljarg) cljarg)
            bootforms   (some->> arg0 slurp util/read-string-all)
            userforms   (when profile? (some->> userscript slurp util/read-string-all))
            commented   (concat () userforms [`(comment "script")] bootforms)
            scriptforms (emit boot? args args* ex commented)
            scriptstr   (str (string/join "\n\n" (map util/pp-str scriptforms)) "\n")]
        (cond
          (:script  opts) (print scriptstr)
          (:version opts) (println boot-version)
          :else
          (do (#'core/init!
                :boot-version boot-version
                :boot-options opts
                :default-task 'tailrecursion.boot.util/help)
              (let [tmpd (core/mktmpdir! ::bootscript)
                    file #(doto (apply io/file %&) io/make-parents)
                    tmpf (.getPath (file tmpd "tailrecursion" "boot" "user.clj"))]
                (core/set-env! :boot-user-ns-file tmpf)
                (doto tmpf (spit scriptstr) (load-file)))))))))
