(ns tailrecursion.boot.core.cli-opts)

(def opts
  [["-f" "--freshen"    "Force snapshot dependency updates."]
   ["-F" "--no-freshen" "Don't update snapshot dependencies."]
   ["-h" "--help"       "Print basic usage and help info."]
   ["-o" "--offline"    "Don't check network for dependencies."]
   ["-P" "--no-profile" "Skip loading of profile.boot script."]
   ["-s" "--script"     "Print generated boot script for debugging."]
   ["-V" "--version"    "Print boot version info."]])
