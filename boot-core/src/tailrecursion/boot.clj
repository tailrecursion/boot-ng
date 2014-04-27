(ns tailrecursion.boot
  (:require
   [clojure.java.io              :as io]
   [clojure.string               :as string]
   [clojure.stacktrace           :as trace]
   [tailrecursion.boot.core.util :as util]
   [tailrecursion.boot.core      :as core :refer [+env+ +boot-dir+]]))

(defn usage []
  (println
    (str
      "Usage: boot [arg ...]\n"
      "       boot <scriptfile.boot> [arg ...]\n")))

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
    ~@forms
    ~(if boot?
       (if edn-ex
         `(binding [*out* *err*]
            (print ~edn-ex)
            (System/exit 1))
         `(core/boot ~@argv*))
       `(when-let [main# (resolve '~'-main)] (main# ~@argv)))))

(defn parse-opts [args]
  (let [opts [["-P" "--no-profile"]
              ["-h" "--help"]
              ["-V" "--version"]]]
    ((juxt :errors :options :arguments)
     (core/parse-opts args opts :in-order true))))

(defn -main [boot-version & [arg0 & args :as args*]]
  (binding [*out* (util/auto-flush *out*)
            *err* (util/auto-flush *err*)]
    (util/exit-ok
      (let [dotboot?    #(.endsWith (.getName (io/file %)) ".boot")
            script?     #(when (and % (.isFile (io/file %)) (dotboot? %)) %)
            bootscript  (io/file (or (:BOOT_SCRIPT +env+) "build.boot"))
            userscript  (script? (io/file +boot-dir+ "profile.boot"))
            [arg0 args] (cond
                          (script? arg0)       [arg0 args]
                          (script? bootscript) [bootscript args*]
                          :else                [nil args*])
            boot?       (contains? #{nil bootscript} arg0)
            [errs opts args] (if boot? (parse-opts args) [nil nil args])
            profile?    (and boot? (not (:no-profile opts)))
            cljarg      (parse-cli args)
            ex          (when (string? cljarg) cljarg)
            args*       (when-not (string? cljarg) cljarg)
            bootforms   (some->> arg0 slurp util/read-string-all)
            userforms   (when profile? (some->> userscript slurp util/read-string-all))
            scriptforms (emit boot? args args* ex (concat () userforms bootforms))
            scriptstr   (str (string/join "\n\n" (map util/pp-str scriptforms)) "\n")]
        (when (:help    opts) (usage) (System/exit 0))
        (when (:version opts) (println boot-version) (System/exit 0))
        (#'core/init!
          :boot-version boot-version
          :default-task 'tailrecursion.boot.util/help)
        (let [tmpd (core/mktmpdir! ::bootscript)
              file #(doto (apply io/file %&) io/make-parents)
              tmpf (.getPath (file tmpd "tailrecursion" "boot" "user.clj"))]
          (core/set-env! :boot-user-ns-file tmpf)
          (doto tmpf (spit scriptstr) (load-file)))))))
