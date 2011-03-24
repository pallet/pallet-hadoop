(ns pallet-cascalog.node
  (:use [pallet.thread-expr :only (for->)]
        [pallet.resource :only (phase)]
        [pallet.crate.automated-admin-user
         :only (automated-admin-user)])
  (:require pallet.compute.vmfest
            [pallet.core :as core]
            [pallet.compute :as compute]
            [pallet.crate.hadoop :as hadoop]
            [pallet.crate.java :as java])
  (:import [java.net InetAddress]))

(def service
  (compute/compute-service-from-config-file :virtualbox))

(def parallel-env {}
  #_{:algorithms
   {:lift-fn pallet.core/parallel-lift
    :vmfest {:create-nodes-fn pallet.compute.vmfest/parallel-create-nodes}
    :converge-fn pallet.core/parallel-adjust-node-counts}})

(def local-proxy (format "http://%s:3128"
                         (.getHostAddress
                          (InetAddress/getLocalHost))))

(def local-node-specs
  (let [default-image  {:image
                        {:os-family :ubuntu
                         :os-64-bit true}}]
    {:tags
     {:hadoop default-image
      :namenode default-image
      :jobtracker default-image
      :slavenode default-image}
     :phases {:bootstrap
              (phase
               (pallet.resource.package/package-manager
                :configure :proxy local-proxy))}
     :proxy local-proxy}))

(def local-env
  (merge local-node-specs parallel-env))

(def hadoop-config
  (phase
   (java/java :jdk)
   hadoop/install
   (hadoop/configure "/tmp/hadoop"
                     :namenode
                     :jobtracker
                     :public
                     {})))

(core/defnode hadoop
  {:inbound-ports [50030 50060 50070]}
  :bootstrap automated-admin-user
  :configure hadoop-config
  :reconfigure hadoop-config)

(defn hadoop-node
  [tag phasemap]
  (-> hadoop
      (assoc-in [:tag] tag)
      (for-> [[key phaseval] phasemap]
             (assoc-in
              [:phases key] phaseval))))

(def name-node
  (hadoop-node
   :namenode
   {:start-namenode (phase
                     (hadoop/name-node "/tmp/node-name/data"))
    :start-hdfs (phase
                 (hadoop/data-node))
    :start-mapred (phase
                   (hadoop/task-tracker))}))

(def job-tracker
  (hadoop-node
   :jobtracker
   {:start-jobtracker (phase
                       (hadoop/job-tracker))
    :start-hdfs (phase
                 (hadoop/data-node))
    :start-mapred (phase
                   (hadoop/task-tracker))}))

(def slave-node
  (hadoop-node
   :slavenode
   {:start-hdfs (phase
                 (hadoop/data-node))
    :start-mapred (phase
                   (hadoop/task-tracker))}))

(defn boot-cluster
  [nodecount]
  (core/converge {name-node 1 job-tracker 1 slave-node nodecount}
                 :compute service
                 :environment local-env))

(defn start-cluster
  []
  (core/lift [name-node job-tracker slave-node]
             :compute service
             :environment local-env
             :phase [:start-namenode :start-jobtracker :start-hdfs :start-mapred]))

(defn kill-cluster
  []
  (core/converge {name-node 0 job-tracker 0 slave-node 0}
                 :compute service
                 :environment local-env))