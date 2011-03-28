(ns pallet-cascalog.node
  (:use [pallet.crate.automated-admin-user
         :only (automated-admin-user)]
        [pallet.thread-expr :only (for->)]
        [pallet.resource :only (phase)]
        [clojure.pprint :only (pprint)])
  (:require [pallet.compute :as compute]
            [pallet.core :as core]
            [pallet.crate.hadoop :as hadoop]
            [pallet.crate.java :as java]
            pallet.compute.vmfest))

(defn debug [req comment & [key-vec]]
  (println "***" comment (or key-vec "(full request)"))
  (if key-vec
    (pprint (get-in req  key-vec))
    (pprint req))
  req)

;;## Local Node Configuration
;;
;; This all has to do with the local node, and is not necessary for
;;the hadoop configuration.
;;
;; TODO -- describe more.

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
    {:tags (zipmap [:hadoop :namenode :jobtracker :slavenode]
                   (repeat default-image))
     :phases {:bootstrap
              (phase
               (pallet.resource.package/package-manager
                :configure :proxy local-proxy))}
     :proxy local-proxy}))

(def local-env
  (merge local-node-specs parallel-env))

;; ## Hadoop Configuration

(defn hadoop-phases
  "TODO -- documentation here, on why we have the ip-type option!"
  [ip-type]
  (let [configure (fn [request]
                    (hadoop/configure request
                                      "/tmp/hadoop"
                                      :namenode
                                      :jobtracker
                                      ip-type
                                      {}))]
    {:bootstrap automated-admin-user
     :configure (phase
                 (java/java :jdk)
                 hadoop/install
                 configure)
     :reconfigure configure
     :publish-ssh-key (phase (hadoop/publish-ssh-key))
     :authorize-jobtracker (phase (hadoop/authorize-jobtracker))
     :start-mapred (phase (hadoop/task-tracker))
     :start-hdfs (phase (hadoop/data-node))
     :start-jobtracker (phase (hadoop/job-tracker))
     :start-namenode (phase (hadoop/name-node
                             "/tmp/node-name/data"))}))

(defn hadoop-node
  "TODO -- docs here!"
  [ip-type tag phaseseq]
  (apply core/make-node
         tag
         {:inbound-ports [50030 50060 50070]}
         (apply concat
                (select-keys (hadoop-phases ip-type)
                             (into [:bootstrap :configure :reconfigure]
                                   phaseseq)))))

;; todo -- does this make sense?
(defn hadoop-cluster
  [ip-type nodecount]
  {:ip-type ip-type
   :nodes {:namenode {:phases [:start-namenode
                               :start-hdfs
                               :start-mapred]
                      :count 1}
           :jobtracker {:phases [:publish-ssh-key
                                 :start-jobtracker
                                 :start-hdfs
                                 :start-mapred]
                        :count 1}
           :slavenode {:phases [:start-hdfs
                                :start-mapred]
                       :count nodecount}}})

;; Here's an example cluster to play with.
(def testcluster (hadoop-cluster :public 10))

(defn describe
  "TODO -- better name!"
  [cluster task]
  (into {}
        (for [[node config] (:nodes cluster)
              :let [[count phases ip-type]
                    (map config [:count :phases :ip-type])
                    node-def (hadoop-node ip-type node phases)]]
          (case task
                :boot [node-def count]
                :kill [node-def 0]))))

(defn node-tags [cluster]
  (keys (:nodes cluster)))

;; TODO -- add methods explaining cluster
;; TODO -- simplify this
(defn cluster-converge
  ([action cluster service env]
     (core/converge (describe cluster action)
                    :compute service
                    :environment env))
  ([action phaseseq cluster service env]
     (core/converge (describe cluster action)
                    :compute service
                    :environment env
                    :phase phaseseq)))

(def boot-cluster
  (partial cluster-converge :boot [:configure
                                   :publish-ssh-key
                                   :authorize-jobtracker]))

(def kill-cluster
  (partial cluster-converge :kill))

;; TODO -- add methods explaining cluster
(defn cluster-lift
  [phaseseq cluster service env]
  (core/lift (node-tags cluster)
             :compute service
             :environment env
             :phase phaseseq))

(def start-cluster
  (partial cluster-lift [:start-namenode
                         :start-hdfs
                         :start-jobtracker
                         :start-mapred]))

;; How to use this thing...
(comment
  (def testcluster (hadoop-cluster :public 10))
  ((comp boot-cluster start-cluster) testcluster service local-env))
