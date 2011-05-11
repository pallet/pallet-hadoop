(ns pallet-cascalog.core
  (:use pallet-cascalog.node
        [pallet.crate.hadoop :only (hadoop-user)]
        [pallet.extensions :only (def-phase-fn phase)])
  (:require [pallet-cascalog.environments :as env]
            [pallet.resource.remote-directory :as rd]
            [pallet.resource.directory :as d]
            [pallet.resource.package :as package]))

;; ### Actual Job Run
;;
;; First, a test cluster. I ran this last night, and all worked wonderfully.

(def-phase-fn install-redd-configs
  "Takes pairs of strings -- the first should be a filename in the
  `reddconfig` bucket on s3, and the second should be the unpacking
  directory on the node."
  [& filename-localpath-pairs]
  (for [[remote local] (partition 2 filename-localpath-pairs)]
    (rd/remote-directory local
                         :url (str "https://reddconfig.s3.amazonaws.com/" remote)
                         :unpack :tar
                         :tar-options "xz"
                         :strip-components 2
                         :owner hadoop-user
                         :group hadoop-user)))


(def fw-path "/usr/local/fwtools")
(def native-path "/home/hadoop/native")
(def serializers
  (apply str (interpose "," ["backtype.hadoop.ThriftSerialization"
                             "forma.FloatsSerialization"
                             "forma.IntsSerialization"
                             "cascading.tuple.hadoop.BytesSerialization"
                             "cascading.tuple.hadoop.TupleSerialization"
                             "org.apache.hadoop.io.serializer.WritableSerialization"
                             "org.apache.hadoop.io.serializer.JavaSerialization"])))

(def-phase-fn config-redd
  "This phase installs the two files that we need to make redd run
  with gdal! We also change the permissions on `/mnt` to allow for
  some heavy duty storage space. We do need some better documentation,
  here."
  []
  (d/directory "/mnt"
               :owner "hadoop"
               :group "hadoop"
               :mode "0755")
  (package/package "libhdf4-dev")
  (install-redd-configs
   "FWTools-linux-x86_64-4.0.0.tar.gz" fw-path
   "linuxnative.tar.gz" native-path))

;; Other options...
;;


(defn forma-cluster [nodecount]
  (let [lib-path (str fw-path "/usr/lib")]
    (cluster-spec :private
                  {:jobtracker (hadoop-node [:jobtracker :namenode])
                   :slaves (slave-node nodecount)}
                  :base-machine-spec {
                                      ;; :hardware-id "cc1.4xlarge"
                                      ;; :image-id "us-east-1/ami-321eed5b"
                                      :os-family :ubuntu
                                      :os-version-matches "10.10"
                                      :os-64-bit true
                                      :fastest true
                                      ;; :spot-price (float 1.60)
                                      }
                  :base-props {:hadoop-env {:JAVA_LIBRARY_PATH native-path
                                            :LD_LIBRARY_PATH lib-path}
                               :hdfs-site {:dfs.data.dir "/mnt/dfs/data"
                                           :dfs.name.dir "/mnt/dfs/name"}
                               :core-site {:io.serializations serializers}
                               :mapred-site {:mapred.tasks.timeout 300000
                                             :mapred.reduce.tasks (int (* 1.2 15 nodecount)) ; 1.2 times the number of tasks times number of nodes
                                             :mapred.tasktracker.map.tasks.maximum 15
                                             :mapred.tasktracker.reduce.tasks.maximum 15
                                             :mapred.child.java.opts (str "-Djava.library.path=" native-path " -Xms1024M -Xmx1024M")
                                             :mapred.child.env (str "LD_LIBRARY_PATH=" lib-path)}})))

(defn forma-boot [node-count]
  (let [cluster (forma-cluster node-count)]
    (do (boot-cluster cluster :compute env/ec2-service :environment env/remote-env)
        (lift-cluster cluster config-redd :compute env/ec2-service :environment env/remote-env)
        (start-cluster cluster :compute env/ec2-service :environment env/remote-env))))

(defn forma-kill []
  (kill-cluster (forma-cluster 0)
                :compute env/ec2-service :environment env/remote-env))
