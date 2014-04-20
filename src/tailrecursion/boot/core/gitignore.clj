(ns tailrecursion.boot.core.gitignore
  (:require
   [tailrecursion.boot.core.classloader :as loader]
   [clojure.java.shell                  :as sh]
   [clojure.java.io                     :as io]
   [clojure.string                      :as string]))

(defprotocol IMatcher
  (-negated? [this] "Is this pattern negated?")
  (-matches? [this f] "Does file f match this pattern?"))

(defrecord Matcher [negated? matcher]
  IMatcher
  (-negated? [this] negated?)
  (-matches? [this f] (matcher f)))

(defn matches? [matchers f]
  (loop [match? nil, [matcher & more-matchers] matchers]
    (if-not matcher
      match?
      (let [m? (-matches? matcher f)
            n? (-negated? matcher)] 
        (recur (if (not m?) match? (not n?)) more-matchers)))))

(defn path-matcher [pattern & [negated?]]
  (Matcher. negated?
    (fn [f] (loader/glob-match? pattern (.toString (.getCanonicalFile f))))))

(defn parse-gitignore1 [pattern base]
  (let [base    (if (.endsWith base "/") base (str base "/"))
        strip   #(string/replace % #"^/*" "")
        pat     (atom pattern)
        mat     (atom [])
        [negated? end-slash? has-slash? lead-slash? lead-asts? end-ast?] 
        (map #(fn [] (re-find % @pat)) [#"^!" #"/$" #"/" #"^/" #"^\*\*/" #"/\*$"])
        neg?    (negated?)
        dir?    (end-slash?)
        matcher #(path-matcher (apply str base %&))]
    (when (negated?) (swap! pat string/replace #"^!" ""))
    (when (end-slash?) (swap! pat string/replace #"/*$" ""))
    (if (lead-slash?)
      (swap! mat conj (matcher (strip @pat)))
      (swap! mat into (map matcher [@pat (str "**/" @pat)])))
    (when (lead-asts?)
      (swap! mat conj (matcher (strip (subs @pat 3)))))
    (when (end-ast?)
      (swap! mat conj (matcher (strip @pat) "*")))
    (Matcher. neg?  (fn [f] (and (or (not dir?) (.isDirectory f))
                                 (some #(-matches? % f) @mat))))))

(defn parse-gitignore [f & [base]]
  (let [base  (or base (-> f io/file (.getCanonicalFile) (.getParent))) 
        skip? #(or (string/blank? %) (re-find #"^\s*#" %))
        lines (->> f slurp (#(string/split % #"\n")) (remove skip?) (map string/trim))]
    (map parse-gitignore1 lines (repeat base))))

(defn core-excludes [& [$GIT_DIR]]
  (let [git #(sh/sh "git" "config" "core.excludesfile")
        cwd (or $GIT_DIR (System/getProperty "user.dir"))]
    (try (-> (git) :out string/trim io/file (parse-gitignore cwd)) (catch Throwable _))))

(defn make-gitignore-matcher [& [$GIT_DIR]]
  (let [cwd         (or $GIT_DIR (System/getProperty "user.dir")) 
        gi-file?    #(= ".gitignore" (.getName %))
        gitignores  (->> (io/file cwd) file-seq (filter gi-file?))
        core-excl   (vec (or (core-excludes $GIT_DIR) []))
        matchers    (into core-excl (mapcat parse-gitignore gitignores))]
    (partial matches? matchers)))
