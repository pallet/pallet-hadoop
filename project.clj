(defproject pallet-hadoop "0.4.0"
  :description "Pallet meets Hadoop."
  :dev-resources-path "dev"
  :repositories {"sonatype"
                 "https://oss.sonatype.org/content/repositories/releases/"}  
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.cloudhoist/pallet "0.6.6"]
                 [org.cloudhoist/hadoop "0.6.0"]
                 [org.cloudhoist/java "0.5.1"]
                 [org.cloudhoist/automated-admin-user "0.6.0"]]
  :dev-dependencies [[org.jclouds/jclouds-compute "1.2.1"]
                     [org.jclouds.driver/jclouds-jsch "1.2.1"]
                     [org.jclouds.driver/jclouds-log4j "1.2.1"]
                     [log4j/log4j "1.2.14"]])
