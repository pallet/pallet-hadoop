(defproject pallet-hadoop "0.1.0"
  :description "Pallet meets Hadoop."
  :dev-resources-path "dev"
  :repositories {"sonatype"
                 "https://oss.sonatype.org/content/repositories/releases/"}  
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.cloudhoist/pallet "0.5.0"]
                 [org.cloudhoist/hadoop "0.5.0"]
                 [org.cloudhoist/automated-admin-user "0.5.0"]
                 [org.cloudhoist/java "0.5.0"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.2.1"]
                     [org.jclouds/jclouds-all "1.0-beta-9c"]
                     [org.jclouds.driver/jclouds-jsch "1.0-beta-9c"]
                     [org.jclouds.driver/jclouds-log4j "1.0-beta-9c"]
                     [log4j/log4j "1.2.14"]
                     [vmfest/vmfest "0.2.2"]])
