(defproject pallet-cascalog "0.0.1-SNAPSHOT"
  :description "A pallet config for cascalog projects"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.cloudhoist/pallet "0.4.8"]
                 [vmfest/vmfest "0.2.3-SNAPSHOT"]
                 [cascalog "1.7.0-SNAPSHOT"]
                 [org.cloudhoist/hadoop "0.4.0-SNAPSHOT"]
                 [org.cloudhoist/automated-admin-user "0.4.0"]
                 [org.cloudhoist/java "0.4.0"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.2.1"]
                     [org.cloudhoist/pallet-lein "0.4.0"]]
  :repositories {"sonatype"
                 "https://oss.sonatype.org/content/repositories/releases/"})
