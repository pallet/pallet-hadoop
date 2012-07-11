(defproject pallet-hadoop "0.3.3-SNAPSHOT"
  :description "Pallet meets Hadoop."
  :dev-resources-path "dev"
  :repositories {"sonatype" "https://oss.sonatype.org/content/repositories/releases/"}  
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.cloudhoist/pallet "0.6.8"]
                 [org.cloudhoist/hadoop "0.7.0-alpha.2"]
                 [org.cloudhoist/java "0.5.1"]
                 [org.cloudhoist/automated-admin-user "0.5.0"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.2.1"]
                     [org.jclouds/jclouds-all "1.0.0"]
                     [org.jclouds.driver/jclouds-jsch "1.0.0"]
                     [org.jclouds.driver/jclouds-log4j "1.0.0"]
                     [log4j/log4j "1.2.14"]
                     [vmfest/vmfest "0.2.2"]])
