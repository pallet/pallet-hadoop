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
;;
;; NOTES ON HOSTNAME RESOLUTION
;;
;; It seems like this is an issue a number of folks are having. We
;;need to populate etc/hosts to skip DNS resolution, if we're going to
;;work on local machines. On EC2, I think we can get around this issue
;;by using the public DNS address.
;;
;; Some discussion here on a way to short circuit DNS --
;;http://www.travishegner.com/2009/06/hadoop-020-on-ubuntu-server-904-jaunty.html
;;
;; But do we want that, really?
;;
;; Looks like we need to do etc/hosts internally -- we could probably
;;do this externally as well, with Amazon's public DNS names and
;;private IP addresses.
;;
;; From here:
;;https://twiki.grid.iu.edu/bin/view/Storage/HadoopUnderstanding
;;
;; For the namenode, etc to be virtualized, you must be able to access
;;them through DNS, or etc/hosts.
;;
;; From HDFS-default --
;;http://hadoop.apache.org/common/docs/r0.20.2/hdfs-default.html
;;
;;dfs.datanode.dns.nameserver -- The host name or IP address of the
;;name server (DNS) which a DataNode should use to determine the host
;;name used by the NameNode for communication and display purposes.
;;
;; More support for using external hostnames on EC2
;; http://getsatisfaction.com/cloudera/topics/hadoop_configuring_a_slaves_hostname
;;
;; How to get hadoop running without DNS --
;; http://db.tmtec.biz/blogs/index.php/get-hadoop-up-and-running-without-dns
;;
;; Using etc/hosts as default --
;; http://www.linuxquestions.org/questions/linux-server-73/how-to-setup-nslookups-queries-using-etc-hosts-as-the-default-654882/
;;
;; And, most clearly:
;;
;; http://www.cloudera.com/blog/2008/12/securing-a-hadoop-cluster-through-a-gateway/
;;
;; One “gotcha” of Hadoop is that the HDFS instance has a canonical
;; name associated with it, based on the DNS name of the machine — not
;; its IP address. If you provide an IP address for the
;; fs.default.name, it will reverse-DNS this back to a DNS name, then
;; subsequent connections will perform a forward-DNS lookup on the
;; canonical DNS name
;;
;; OTHER NOTES
;;
;; Hadoop cluster tips and tricks --
;; http://allthingshadoop.com/2010/04/28/map-reduce-tips-tricks-your-first-real-cluster/
;;
;; Discussion of rack awareness --
;; http://hadoop.apache.org/common/docs/r0.19.2/cluster_setup.html#Configuration+Files
;;
;; Hadoop tutorial --
;; http://developer.yahoo.com/hadoop/tutorial/module7.html
;;
;; KEY NOTES;; From Noll link:
;; http://www.mail-archive.com/common-user@hadoop.apache.org/msg00170.html
;; http://search-hadoop.com/m/PcJ6xnNrSo1/Error+reading+task+output+http/v=threaded
;; From a note here:
;; http://www.michael-noll.com/tutorials/running-hadoop-on-ubuntu-linux-multi-node-cluster/#confmasters-master-only
;;
;; So, we can probably do this with etc/hosts.

(defn hadoop-phases
  "TODO -- documentation here, on why we have the ip-type option! TODO
  - -can we get this ip-type from the cluster definition, somehow?
  Where does it need to exist, given that the configuration step is
  the only only one that needs to know?"
  [ip-type]
  (let [configure (phase
                   (hadoop/configure :namenode
                                     :jobtracker
                                     ip-type
                                     {}))]
    {:bootstrap automated-admin-user
     :configure (phase (java/java :jdk)
                       hadoop/install
                       configure)
     :reinstall (phase hadoop/install
                       configure)
     :reconfigure configure
     :publish-ssh-key hadoop/publish-ssh-key
     :authorize-jobtracker hadoop/authorize-jobtracker
     :start-mapred hadoop/task-tracker
     :start-hdfs hadoop/data-node
     :start-jobtracker hadoop/job-tracker
     :start-namenode (hadoop/name-node "/tmp/node-name/data")}))

(defn hadoop-node
  "TODO -- docs here! We're creating a hadoop node, but what kind?
  What phases does it have? We should think about merging the
  phaseseqs before we send anything into hadoop-node. Might that be
  the cluster's responsibility?"
  [ip-type tag phaseseq]
  (let [default-phases [:bootstrap
                        :reinstall
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
  (boot-cluster testcluster env/vm-service env/vm-env)
  (start-cluster testcluster env/vm-service env/vm-env)
  (kill-cluster testcluster env/vm-service env/vm-env))