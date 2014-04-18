(ns tailrecursion.boot.core
  (:require
   [tailrecursion.boot.core.util :as util]))

(defn- resolve-cl [sym]
  (require 'tailrecursion.boot.core.classloader)
  (resolve (symbol "tailrecursion.boot.core.classloader" (name sym))))

(defn- make-cl-fn [sym]
  (fn [& args] (apply (resolve-cl sym) args)))

(def add-dirs!     (make-cl-fn 'add-dirs!))
(def resolve-deps! (make-cl-fn 'resolve-deps!))
(def add-deps!     (make-cl-fn 'add-deps!))
(def make-pod      (make-cl-fn 'make-pod))
