(defproject pallet-hadoop "0.0.1-SNAPSHOT"
  :description "A pallet config for hadoop projects"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.cloudhoist/pallet "0.4.17"
                  :exclusions [org.jclouds/jclouds-compute
                               org.jclouds/jclouds-blobstore
                               org.jclouds/jclouds-scriptbuilder
                               org.jclouds/jclouds-aws
                               org.jclouds/jclouds-bluelock
                               org.jclouds/jclouds-gogrid
                               org.jclouds/jclouds-rackspace
                               org.jclouds/jclouds-rimuhosting
                               org.jclouds/jclouds-slicehost
                               org.jclouds/jclouds-terremark
                               org.jclouds/jclouds-jsch
                               org.jclouds/jclouds-log4j
                               org.jclouds/jclouds-enterprise]]
                 [org.cloudhoist/pallet-crates-standalone "0.4.0"]
                 [org.jclouds/jclouds-all "1.0-beta-9b"]
                 [org.jclouds.driver/jclouds-jsch "1.0-beta-9b"]
                 [org.jclouds.driver/jclouds-log4j "1.0-beta-9b"]
                 [org.jclouds.driver/jclouds-enterprise "1.0-beta-9b"]
                 [com.jcraft/jsch "0.1.42"]
                 [log4j/log4j "1.2.14"]
                 [org.cloudhoist/hadoop "0.4.0-SNAPSHOT"]
                 [org.cloudhoist/automated-admin-user "0.4.0"]
                 [org.cloudhoist/java "0.4.0"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.2.1"]
                     [vmfest/vmfest "0.2.2"]]
  :repositories {"sonatype"
                 "https://oss.sonatype.org/content/repositories/releases/"})