(ns pallet-cascalog.node
  (:use [pallet.crate.automated-admin-user
         :only (automated-admin-user)]
        [pallet.thread-expr :only (for->)]
        [pallet.resource :only (phase)]
        [clojure.pprint :only (pprint)])
  (:require [pallet-cascalog.environments :as env]
            [pallet.compute :as compute]
            [pallet.core :as core]
            [pallet.crate.hadoop :as hadoop]
            [pallet.crate.java :as java]
            pallet.compute.vmfest))

(defn debug [req comment & [key-vec]]
  (do (println "***" comment (or key-vec "(full request)"))
      (if key-vec
        (pprint (get-in req  key-vec))
        (pprint req))
      req))

;; ## Hadoop Configuration

;; TODO -- some serious documentation on this bad boy!
;;
;; Merge in and test Toni's stuff
;;
;; Test hadoop starting procedure!
;;
;; Install the current hadoop to maven, maybe, so we stop getting such
;;weird errors.
;;
;; NOTES:
;;
;; A cluster should take in a map of arguments (ip-type, for example)
;; and a map of node descriptions, including base nodes for each node
;; type, and output a cluster object. We should have a layer of
;; abstraction on top of nodes, etc.

(defn hadoop-phases
  "TODO -- documentation here, on why we have the ip-type option! TODO
  - -can we get this ip-type from the cluster definition, somehow?
  Where does it need to exist, given that the configuration step is
  the only only one that needs to know?"
  [ip-type]
  (let [configure (fn [request]
                    (hadoop/configure request
                                      :namenode
                                      :jobtracker
                                      ip-type
                                      {}))]
    {:bootstrap automated-admin-user
     :configure (phase
                 (java/java :jdk)
                 hadoop/install
                 configure)
     :reconfigure (phase (hadoop/configure
                          :namenode
                          :jobtracker
                          ip-type
                          {}))
     :publish-ssh-key (phase (hadoop/publish-ssh-key))
     :authorize-jobtracker (phase (hadoop/authorize-jobtracker))
     :start-mapred (phase (hadoop/task-tracker))
     :start-hdfs (phase (hadoop/data-node))
     :start-jobtracker (phase (hadoop/job-tracker))
     :start-namenode (phase (hadoop/name-node "/tmp/node-name/data"))}))

(defn hadoop-node
  "TODO -- docs here! We're creating a hadoop node, but what kind?
  What phases does it have? We should think about merging the
  phaseseqs before we send anything into hadoop-node. Might that be
  the cluster's responsibility?"
  [ip-type tag phaseseq]
  (let [default-phases [:bootstrap
                        :configure
                        :reconfigure
                        :authorize-jobtracker]]
    (apply core/make-node
           tag
           {}
           #_{:inbound-ports [50030 50060 50070]}
           (apply concat
                  (select-keys (hadoop-phases ip-type)
                               (into default-phases
                                     phaseseq))))))

;; todo -- does this make sense? Should we call it `make-cluster`?

(defn cluster-def
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

(defn describe
  "TODO -- better name! Also, maybe the task input could be :lift, for
describe."
  ([cluster]
     (keys (describe cluster :kill)))
  ([cluster task]
     (into {}
           (for [[node config] (:nodes cluster)
                 :let [ip-type (:ip-type cluster)
                       [count phases] (map config [:count :phases])
                       node-def (hadoop-node ip-type node phases)]]
             (case task
                   :boot [node-def count]
                   :kill [node-def 0])))))

;; TODO -- add methods explaining cluster
;; TODO -- simplify this
(defn converge-cluster
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
  (partial converge-cluster :boot [:configure
                                   :publish-ssh-key
                                   :authorize-jobtracker]))

(def kill-cluster
  (partial converge-cluster :kill))

(defn lift-cluster
  [phaseseq cluster service env]
  (core/lift (describe cluster)
             :compute service
             :environment env
             :phase phaseseq))

(def start-cluster
  (partial lift-cluster [:start-namenode
                         :start-hdfs
                         :start-jobtracker
                         :start-mapred]))

;; How to use this thing...
(comment
  (def testcluster (cluster-def :public 0))
  ((comp boot-cluster start-cluster) testcluster env/vm-service env/vm-env))