(defproject pallet-hadoop "0.1.0-SNAPSHOT"
  :description "Pallet meets Hadoop."
  :dev-resources-path "dev"
  :repositories {"sonatype-release"
                 "https://oss.sonatype.org/content/repositories/releases/"
                 "sonatype-snap"
                 "https://oss.sonatype.org/content/repositories/snapshots/"}  
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.cloudhoist/pallet "0.5.0"]
                 [org.cloudhoist/hadoop "0.5.0-SNAPSHOT"]
                 [org.cloudhoist/automated-admin-user "0.5.0"]
                 [org.cloudhoist/java "0.5.0"]
                 [org.jclouds/jclouds-aws "1.0-SNAPSHOT"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.2.1"]])
