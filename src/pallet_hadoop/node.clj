(ns pallet-hadoop.node
  (:use [pallet-hadoop util phases]
        [pallet.crate.automated-admin-user :only (automated-admin-user)]
        [pallet.extensions :only (phase def-phase-fn)]
        [pallet.crate.java :only (java)]
        [pallet.core :only (make-node lift converge)]
        [pallet.compute :only (primary-ip nodes-by-tag nodes)])
  (:require [pallet.core :as core]
            [pallet.action.package :as package]
            [pallet.crate.hadoop :as h]))

;; ## Hadoop Cluster Configuration

;; ### Defaults

(def
  ^{:doc "Map between hadoop aliases and the roles for which they
  stand.`:slavenode` acts as an alias for nodes that function as both
  datanodes and tasktrackers."}
  hadoop-aliases
  {:slavenode [:datanode :tasktracker]})

(defn expand-aliases
  "Returns a sequence of hadoop roles, with any aliases replaced by
  the corresponding roles. `:slavenode` is the only alias, currently,
  and expands out to `:datanode` and `:tasktracker`."
  [roles]
  (->> roles
       (replace hadoop-aliases)
       (apply merge-to-vec)))

(def
  ^{:doc "Set of all hadoop `master` level tags. Used to assign
  default counts to master nodes, and to make sure that no more than
  one of each exists."}
  hadoop-masters
  #{:namenode :jobtracker})

(defn master?
  "Predicate to determine whether or not the supplied sequence of
  roles contains a master node tag."
  [roleseq]
  (boolean (some hadoop-masters roleseq)))

;; Hadoop requires certain ports to be accessible, as discussed
;; [here](http://goo.gl/nKk3B) by the folks at Cloudera. We provide
;; sets of ports that can be merged based on the hadoop roles that
;; some node-spec wants to use.

(def hadoop-ports
  {:default #{22 80}
   :namenode #{50070 8020}
   :datanode #{50075 50010 50020}
   :jobtracker #{50030 8021}
   :tasktracker #{50060}
   :secondary-namenode #{50090 50105}})

(defn get-tags
  "Accept a map of `tag, node-group` and sequence of hadoop roles
  pairs and returns a sequence of the corresponding node tags. Every
  role must exist in the supplied node-def map to make it past the
  postcondition."
  [node-groups role-seq]
  {:post [(= (count %)
             (count role-seq))]}
  (filter identity
          (for [role role-seq]
            (some (fn [[tag def]]
                    (when (some #{role} (get-in def [:node :roles]))
                      tag))
                  node-groups))))

(defn hadoop-phases
  "Returns a map of all possible hadoop phases. IP-type specifies..."
  [ip-type jt-tag nn-tag properties]
  {:pre [(#{:public :private} ip-type)]}
  (let [configure (phase
                   (h/configure ip-type nn-tag jt-tag properties))]
    {:bootstrap automated-admin-user
     :configure (phase (package/package-manager :update)
                       (package/package-manager :upgrade)
                       (java :jdk)
                       (h/install :cloudera)
                       configure)
     :reinstall (phase (h/install :cloudera)
                       configure)
     :reconfigure configure
     :publish-ssh-key h/publish-ssh-key
     :authorize-jobtracker (phase (h/authorize-tag jt-tag))
     :start-mapred h/task-tracker
     :start-hdfs h/data-node
     :start-jobtracker h/job-tracker
     :start-namenode (phase (h/name-node "/tmp/node-name/data"))}))

(def role->phase-map
  {:default     #{:bootstrap
                  :reinstall
                  :configure
                  :reconfigure
                  :authorize-jobtracker}
   :namenode    #{:start-namenode}
   :datanode    #{:start-hdfs}
   :jobtracker  #{:publish-ssh-key :start-jobtracker}
   :tasktracker #{:start-mapred}})

(defn roles->phases
  "Converts a sequence of hadoop roles into a sequence of pallet phases
  required by a node trying to take on each of these roles."
  [roles]
  (getmerge role->phase-map roles))

(defn base-spec
  "Returns a hadoop server-spec for the supplied sequence of
  roles."
  [phase-map role-seq]
  (let [ports       (getmerge hadoop-ports role-seq)
        phase-names (roles->phases role-seq)]
    (server-spec
     :phases    (select-keys phase-map phase-names)
     :node-spec (node-spec
                 :network {:inbound-ports ports}))))

(defn hadoop-server-spec
  "Returns a new hadoop node map generated by merging the supplied
  node into the base specs defined by the supplied cluster."
  [cluster node]
  {:post [(some (partial contains? role->phase-map) (:roles %))]}
  (let [{:keys [ip-type node-groups]} cluster
        [jt-tag nn-tag] (get-tags node-groups [:jobtracker :namenode])
        phase-map (->> (h/merge-config properties (:properties node))
                       (hadoop-phases ip-type jt-tag nn-tag properties))
        role-seq  (-> (expand-aliases (:roles node))
                      (conj :default))]
    (server-spec
     :roles   role-seq
     :extends (base-spec phase-map role-seq))))

(defn hadoop-group-spec
  "Generates a pallet representation of a hadoop node, built from the
  supplied cluster and the supplied hadoop node map -- see
  `node-group` for construction details."
  [cluster tag node]
  (group-spec tag
              :count     (:count node)
              :node-spec (:node-spec node)
              :extends   (hadoop-server-spec cluster node)))

(defn hadoop-cluster-spec
  [cluster-name & {:keys [ip-type node-groups node-spec] :as cluster}]
  (cluster-spec cluster-name
                :node-spec node-spec
                :groups (for [[tag node-map] (:node-groups cluster)]
                          (hadoop-group-spec cluster tag node-map))))

(defn node-group
  "Generates a map representation of a Hadoop node. For example:

   (node-group [:slavenode] 10)
    => {:roles [:slavenode]
        :node-spec  {}
        :properties {}
        :count      10}"
  [role-seq & [count & {:keys [node-spec properties]}]]
  {:pre [(if (master? role-seq)
           (or (nil? count) (= count 1))
           count)]}
  {:roles       role-seq
   :node-spec  (or node-spec  {})
   :properties (or properties {})
   :count      (or count 1)})

(def slave-group (partial node-group [:slavenode]))

;; ### Cluster Level Converge and Lift

(defn boot-cluster
  "Launches all nodes in the supplied cluster, installs hadoop and
  enables SSH access between jobtracker and all other nodes. See
  `pallet.core/converge` for details on acceptable options."
  [cluster & options]
  (apply core/converge
         cluster
         :phase [:configure
                 :publish-ssh-key
                 :authorize-jobtracker]
         options))

(defn start-cluster
  "Starts up all hadoop services on the supplied cluster. See
  `pallet.core/lift` for details on acceptable options. (All are valid
  except `:phase`, for now."
  [cluster & options]
  (apply core/lift
         cluster
         :phase [:start-namenode
                 :start-hdfs
                 :start-jobtracker
                 :start-mapred]
         options))

(defn kill-cluster
  "Converges cluster with counts of zero for all nodes, shutting down
  everything. See `pallet.core/converge` for details on acceptable
  options."
  [cluster & options]
  (apply core/converge
         {cluster 0}
         options))

;; ## Helper Functions

(defn jobtracker-ip
  "Returns a string containing the IP address of the jobtracker node
  instantiated in the service."
  [service]
  (when-let [[jobtracker] (nodes-with-role service :jobtracker)]
    (primary-ip jobtracker)))

(defn namenode-ip
  "Returns a string containing the IP address of the namenode
  instantiated in the service, if there is one"
  [service]
  (when-let [[namenode] (nodes-with-role service :namenode)]
    (primary-ip namenode)))

(comment
  "This'll get you started; for a more detailed introduction, please
   head over to https://github.com/pallet/pallet-hadoop-example."

  (use 'pallet-hadoop.node)
  (use 'pallet.compute)

  ;; We can define our compute service here...
  (def ec2-service
    (compute-service "aws-ec2"
                     :identity "ec2-access-key-id"
                     :credential "ec2-secret-access-key"))
  
  ;; Or, we can get this from a config file, in
  ;; `~/.pallet/config.clj`.
  (def ec2-service
    (service :aws))

  (def example-cluster
    (hadoop-cluster "cluster-name"
                    :ip-type :private
                    :node-spec {:os-family :ubuntu
                                :os-version-matches "10.10"
                                :os-64-bit true}
                    :node-groups {:jobtracker (node-group [:jobtracker :namenode])
                                  :slaves (slave-group 1)}
                    :properties
                    {:mapred-site {:mapred.task.timeout 300000
                                   :mapred.reduce.tasks 3
                                   :mapred.tasktracker.map.tasks.maximum 3
                                   :mapred.tasktracker.reduce.tasks.maximum 3
                                   :mapred.child.java.opts "-Xms1024m"}}))
  
  (boot-cluster  example-cluster :compute ec2-service)
  (start-cluster example-cluster :compute ec2-service))
