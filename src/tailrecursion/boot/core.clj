(ns tailrecursion.boot.core
  (:require
   [tailrecursion.boot.core.util        :as util]
   [tailrecursion.boot.core.classloader :as load]))

(def dependencies  load/dependencies)
(def resolve-deps! load/resolve-deps!)
(def add-deps!     load/add-deps!)
(def add-dirs!     load/add-dirs!)
(def make-pod      load/make-pod)
