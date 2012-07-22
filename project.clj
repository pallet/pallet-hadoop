(defproject org.cloudhoist/pallet-hadoop "0.3.3-beta.5-SNAPSHOT"
  :description "Pallet meets Hadoop."
  :dev-resources-path "dev"
  :repositories {"sonatype" "https://oss.sonatype.org/content/repositories/releases/"}  
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.cloudhoist/pallet "0.7.0"]
                 [org.cloudhoist/hadoop "0.7.0-beta.2"]
                 [org.cloudhoist/java "0.5.1"]
                 [org.cloudhoist/automated-admin-user "0.5.0"]]
  :dev-dependencies [[org.jclouds/jclouds-all "1.4.1"]
                     [org.jclouds.driver/jclouds-jsch "1.4.1"]
                     [org.jclouds.driver/jclouds-slf4j "1.4.1"]
                     [org.cloudhoist/pallet-jclouds "1.4.0-beta.1"]
                     [ch.qos.logback/logback-classic "1.0.1"]
                     [ch.qos.logback/logback-core "1.0.1"]])
