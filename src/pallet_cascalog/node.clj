(ns pallet-cascalog.node
  (:use [pallet.crate.automated-admin-user
         :only (automated-admin-user)]
        [pallet.crate.hadoop :only (phase-fn)]
        [clojure.pprint :only (pprint)]
        [clojure.set :only (union)])
  (:require [pallet-cascalog.environments :as env]
            [pallet.compute :as compute]
            [pallet.core :as core]
            [pallet.crate.hadoop :as hadoop]
            [pallet.crate.java :as java]
            pallet.compute.vmfest))

(defn debug [req comment & [key-vec]]
  (do (println "***" comment (or key-vec "(full request)"))
      (if key-vec
        (pprint (get-in req key-vec))
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
  [ip-type jt-tag nn-tag properties]
  (let [configure (phase-fn [] (hadoop/configure ip-type
                                                 nn-tag
                                                 jt-tag
                                                 properties))]
    {:bootstrap automated-admin-user
     :configure (phase-fn []
                          (java/java :jdk)
                          hadoop/install
                          configure)
     :reinstall (phase-fn []
                          hadoop/install
                          configure)
     :reconfigure configure
     :publish-ssh-key hadoop/publish-ssh-key
     :authorize-jobtracker hadoop/authorize-jobtracker
     :start-mapred hadoop/task-tracker
     :start-hdfs hadoop/data-node
     :start-jobtracker hadoop/job-tracker
     :start-namenode (phase-fn [] (hadoop/name-node "/tmp/node-name/data"))}))

;; Hadoop doesn't really have too many requirements for its nodes --
;; we do have to layer a few properties onto the base nodespec,
;; however. The ports are the most important element. If the user
;; supplies some default set of ports, we'd like to some way to take
;; the union of the required hadoop ports and the supplied ports from
;; the base node. The following function converts all supplied
;; sequences to sets, takes the union, and converts the results back
;; into a vector. Any type of collection is fine, for `xs`.

(defn merge-to-vec
  "Returns a vector containing the union of all supplied collections."
  [& xs]
  (apply (comp vec union) (map set xs)))


;; We've aliased `:slavenode` to `:datanode` and `:tasktracker`, as
;; these usually come together.

(def hadoop-aliases {:slavenode [:datanode :tasktracker]})

(defn expand-aliases
  "Returns a sequence of hadoop roles, with any aliases replaced by
  the corresponding roles. `:slavenode` is the only alias, currently,
  and expands out to `:datanode` and `:tasktracker`."
  [roles]
  (flatten (replace hadoop-aliases (conj roles :default))))

;; Finally, the big method! By providing a base node and a vector of
;; hadoop "roles", the user gets back a new node-spec with all
;; required hadoop modifications.

;; Hadoop requires certain ports to be accessible, as discussed
;; [here](http://goo.gl/nKk3B) by the folks at Cloudera. We provide
;; sets of ports that can be merged based on the hadoop roles that
;; some node-spec wants to use.

(def hadoop-ports {:default #{22 80}
                   :namenode #{50070 8020}
                   :datanode #{50075 50010 50020}
                   :jobtracker #{50030 8021}
                   :tasktracker #{50060}
                   :secondarynamenode #{50090 50105}})

;; TODO -- Is this going to get confusing? CAN WE ASSUME that all
;; aliases have been expanded, or will it clearer if we do otherwise?

(defn hadoop-machine-spec
  "Generates a node spec for a hadoop node, merging together the given
  basenode with the required properties for the defined hadoop
  roles. (We assume at this point that all aliases have been expanded.)"
  [base-spec roles]
  (let [ports (apply merge-to-vec (map hadoop-ports roles))]
    (merge-with merge-to-vec
                base-spec
                {:inbound-ports ports})))

(def hadoop-masters ^{:doc "Set of all hadoop `master` level
  tags. Used to assign default counts to master nodes, and to make
  sure that no more than one of each exists."}
  #{:namenode :jobtracker})
(defn master?
  "Predicate that determines whether or not the given sequence of
  roles contains a master node tag."
  [roleseq]
  (boolean (some hadoop-masters roleseq)))

(def
  ^{:doc "Map of all hadoop roles to sets of required phases."}
  role->phase-map
  {:default #{:bootstrap
              :reinstall
              :configure
              :reconfigure
              :authorize-jobtracker}
   :namenode #{:start-namenode}
   :datanode #{:start-hdfs}
   :jobtracker #{:publish-ssh-key :start-jobtracker}
   :tasktracker #{:start-mapred}})

(defn hadoop-server-spec
  "Returns a map of all all hadoop phases -- we'll need to modify the
  name, here, and change this to compose with over server-specs."
  [ip-type jt-tag nn-tag properties roles]
  (let [phasemap (hadoop-phases ip-type jt-tag
                                nn-tag properties)]
    (apply concat
           (select-keys phasemap
                        (apply merge-to-vec
                               (map role->phase-map roles))))))

;; We have a precondition here that makes sure at least one of the
;;defined roles exists as a hadoop roles.
;;
;; TODO -- is this the best way to check that all roles are fulfilled?
(defn hadoop-spec
  "Equivalent to `server-spec` in the new pallet."
  [tag ip-type jt-tag nn-tag base-spec {:keys [spec roles props]
                                        :or {spec {}
                                             props {}}}]
  {:pre [(some (set (keys role->phase-map)) (expand-aliases roles))]}
  (let [roles (expand-aliases roles)
        spec (merge base-spec spec)]
    (apply core/make-node
           tag
           (hadoop-machine-spec spec roles)
           (hadoop-server-spec ip-type jt-tag nn-tag props roles))))

(defn hadoop-node
  "Generates a map representation of a Hadoop node, employing sane defaults."
  [roles & [count & {:keys [base-spec props]
                     :or {base-spec {} props {}}
                     :as options}]]
  {:pre [(or count (master? roles))]}
  {:node (merge {:roles roles} options)
   :count (or count (when (master? roles) 1))})

(def slave-node (partial hadoop-node [:slavenode]))

;; TODO -- We'll want to think about a way to check that only one role
;; for each master node exists.
(defn roles->tags
  "Accepts a map of `tag, hadoop-node` pairs and a sequence of roles,
  and returns a sequence of the corresponding node tags. A
  postcondition is implemented to make sure that every role exists in
  the given node-def map."
  [role-seq node-defs]
  {:post [(= (count %) (count role-seq))]}
  (let [find-tag (fn [k]
                   (some (fn [[tag def]]
                           (when (some #{k} (get-in def [:node :roles]))
                             tag))
                         node-defs))]
    (remove nil? (map find-tag role-seq))))

(defn cluster->node-map
  "Converts a cluster to `node-map` represention, for use in a call to
  `pallet.core/converge`. Supported tasks at this time are `:boot` and `:kill`.

    :boot => uses the counts defined in the cluster
    :kill => sets map values to zero, effectively killing the cluster on converge."
  [cluster task]
  (let [[node-defs base-spec ip-type] (map cluster
                                           [:nodedefs :base-machine-spec :ip-type])
        [jt-tag nn-tag] (roles->tags [:jobtracker :namenode] node-defs)]
    (into {}
          (for [[tag config] node-defs
                :let [[count node] (map config [:count :node])
                      node-def (hadoop-spec tag ip-type jt-tag nn-tag
                                            base-spec node)]]
            (case task
                  :boot [node-def count]
                  :kill [node-def 0])))))

;; TODO -- better name! Also, maybe the task input could be :lift, for
;; describe.
(defn cluster->node-set
  "Converts a cluster to `node-set` represention, for use in a call to
  `pallet.core/lift`."
  [cluster]
  (keys (cluster->node-map cluster :kill)))

;; TODO -- add methods explaining cluster
;; TODO -- simplify this
(defn converge-cluster
  ([action cluster service env]
     (core/converge (cluster->node-map cluster action)
                    :compute service
                    :environment env))
  ([action phaseseq cluster service env]
     (core/converge (cluster->node-map cluster action)
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
  (core/lift (cluster->node-set cluster)
             :compute service
             :environment env
             :phase phaseseq))

(def start-cluster
  (partial lift-cluster [:start-namenode
                         :start-hdfs
                         :start-jobtracker
                         :start-mapred]))

;; TODO -- add overall cluster default hadoop properties.
(def cluster-spec
  {:base-machine-spec {}
   :ip-type :private
   :nodedefs {:namenode    (hadoop-node [:namenode :slavenode] 1)
              :jobtracker  (hadoop-node [:jobtracker :slavenode])
              :slaves      (slave-node 1)
              :spot-slaves (slave-node 5 :base-spec {:spot-price (float 0.03)})}})

;; How to use this thing...
(comment
  (boot-cluster cluster-spec env/vm-service env/vm-env)
  (boot-cluster cluster-spec env/ec2-service env/remote-env)

  (start-cluster cluster-spec env/vm-service env/vm-env)
  (start-cluster cluster-spec env/ec2-service env/remote-env)

  (kill-cluster cluster-spec env/vm-service env/vm-env)
  (kill-cluster cluster-spec env/ec2-service env/remote-env))