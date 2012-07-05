(ns pallet-hadoop.node
  (:use [pallet.crate.automated-admin-user :only (automated-admin-user)]
        [pallet.extensions :only (phase def-phase-fn)]
        [pallet.crate.java :only (java)]
        [pallet.core :only (make-node lift converge)]
        [pallet.compute :only (running? primary-ip private-ip nodes-by-tag nodes)]
        [clojure.set :only (union)])
  (:require [pallet.core :as core]
            [pallet.action.package :as package]
            [pallet.crate.hadoop :as h]))

;; ## Hadoop Cluster Configuration

;; ### Utilities

(defn merge-to-vec
  "Returns a vector representation of the union of all supplied
  items. Entries in xs can be collections or individual items. For
  example,

  (merge-to-vec [1 2] :help 2 1)
  => [1 2 :help]"
  [& xs]
  (->> xs
       (map #(if (coll? %) (set %) #{%}))
       (apply (comp vec union))))

(defn set-vals
  "Sets all entries of the supplied map equal to the supplied value."
  [map val]
  (zipmap (keys map)
          (repeat val)))

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

(def
  ^{:doc "Hadoop requires certain ports to be accessible, as discussed
  [here](http://goo.gl/nKk3B) by the folks at Cloudera. We provide
  sets of ports that can be merged based on the hadoop roles that some
  node-spec wants to use."}
  hadoop-ports
  {:default #{22 80}
   :namenode #{50070 8020}
   :datanode #{50075 50010 50020}
   :jobtracker #{50030 8021}
   :tasktracker #{50060}
   :secondarynamenode #{50090 50105}})

(def role->phase-map
  {:default #{:bootstrap
              :reinstall
              :configure
              :reconfigure
              :authorize-jobtracker}
   :namenode #{:start-namenode}
   :datanode #{:start-hdfs}
   :jobtracker #{:publish-ssh-key :start-jobtracker}
   :tasktracker #{:start-mapred}})

(defn roles->tags
  "Accepts sequence of hadoop roles and a map of `tag, node-group`
  pairs and returns a sequence of the corresponding node tags. Every
  role must exist in the supplied node-def map to make it past the
  postcondition."
  [role-seq node-defs]
  {:post [(= (count %)
             (count role-seq))]}
  (remove nil?
          (for [role role-seq]
            (some (fn [[tag def]]
                    (when (some #{role} (get-in def [:node :roles]))
                      tag))
                  node-defs))))

(defn roles->phases
  "Converts a sequence of hadoop roles into a sequence of pallet
  phases required by a node trying to take on each of these roles."
  [roles]
  (->> roles (mapcat role->phase-map) distinct vec))

(defn hadoop-phases
  "Returns a map of all possible hadoop phases. IP-type specifies..."
  [{:keys [nodedefs ip-type]} properties]
  (let [[jt-tag nn-tag] (roles->tags [:jobtracker :namenode] nodedefs)
        configure (phase (h/configure ip-type nn-tag jt-tag properties))]
    {:bootstrap automated-admin-user
     :configure (phase (package/package-manager :update)
                       (package/package-manager :upgrade)
                       (java :openjdk)
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

(defn hadoop-machine-spec
  "Generates a pallet node spec for the supplied hadoop node,
  merging together the given base map with properties required to
  support the attached hadoop roles."
  [{:keys [spec roles]}]
  (let [ports (->> roles (mapcat hadoop-ports) distinct vec)]
    (merge-with merge-to-vec
                spec
                {:inbound-ports ports})))

(defn hadoop-server-spec
  "Returns a map of all hadoop phases. `hadoop-server-spec` currently
  doesn't compose with existing hadoop phases. This will change soon."
  [cluster {:keys [props roles]}]
  (select-keys (hadoop-phases cluster props)
               (roles->phases roles)))

(defn merge-node
  "Returns a new hadoop node map generated by merging the supplied
  node into the base specs defined by the supplied cluster."
  [cluster node]
  {:post [(some (partial contains? role->phase-map) (:roles %))]}
  (let [{:keys [base-machine-spec base-props]} cluster
        {:keys [spec roles props]} node]
    {:spec (merge base-machine-spec spec)
     :props (h/merge-config base-props props)
     :roles (-> roles
                (conj :default)
                expand-aliases)}))

(defn hadoop-spec
  "Generates a pallet representation of a hadoop node, built from the
  supplied cluster and the supplied hadoop node map -- see
  `node-group` for construction details. (`hadoop-spec` is similar to
  `pallet.core/defnode`, sans binding.)"
  [cluster tag node]
  (let [node (merge-node cluster node)]
    (apply core/make-node
           tag
           (hadoop-machine-spec node)
           (apply concat (hadoop-server-spec cluster node)))))

(defn node-group
  "Generates a map representation of a Hadoop node. For example:

   (node-group [:slavenode] 10)
    => {:node {:roles [:tasktracker :datanode]
               :spec {}
               :props {}}
       :count 10}"
  [role-seq & [count & {:keys [spec props]}]]
  {:pre [(if (master? role-seq)
           (or (nil? count) (= count 1))
           count)]}
  {:node {:roles role-seq
          :spec (or spec {})
          :props (or props {})}
   :count (or count 1)})

(def slave-group (partial node-group [:slavenode]))

(defn cluster-spec
  "Generates a data representation of a hadoop cluster.

    ip-type: `:public` or `:private`. (Hadoop keeps track of
  jobtracker and namenode identity via IP address. This option toggles
  the type of IP address used. (EC2 requires `:private`, while a local
  cluster running on virtual machines will require `:public`."
  [ip-type nodedefs & {:as options}]
  {:pre [(#{:public :private} ip-type)]}
  (merge {:base-machine-spec {}
          :base-props {}}
         options
         {:ip-type ip-type
          :nodedefs nodedefs}))

(defn cluster->node-map
  "Converts a cluster to `node-map` represention, for use in a call to
  `pallet.core/converge`."
  [cluster]
  (into {}
        (for [[tag {:keys [count node]}] (:nodedefs cluster)]
          [(hadoop-spec cluster tag node) count])))

(defn cluster->node-set
  "Converts a cluster to `node-set` represention, for use in a call to
  `pallet.core/lift`."
  [cluster]
  (keys (cluster->node-map cluster)))

;; ### Cluster Level Converge and Lift

(defn converge-cluster
  "Identical to `pallet.core/converge`, with `cluster` taking the
  place of `node-map`."
  [cluster & options]
  (apply core/converge
         (cluster->node-map cluster)
         options))

(defn lift-cluster
  "Identical to `pallet.core/lift`, with `cluster` taking the
  place of `node-set`."
  [cluster & options]
  (apply core/lift
         (cluster->node-set cluster)
         options))

(defn boot-cluster
  "Launches all nodes in the supplied cluster, installs hadoop and
  enables SSH access between jobtracker and all other nodes. See
  `pallet.core/converge` for details on acceptable options."
  [cluster & options]
  (apply converge-cluster
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
  (apply lift-cluster
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
         (-> (cluster->node-map cluster)
             (set-vals 0))
         options))

;; helper functions

(defn master-ip
  "Returns a string containing the IP address of the master node
  instantiated in the service."
  [service tag-kwd ip-type]
  ;; We need to make sure we only check for running nodes, as if you
  ;; rebuild the cluster EC2 will report both running and terminated
  ;; nodes for quite a while.
  (when-let [master-node (first
                          (filter running?
                                  (tag-kwd (nodes-by-tag (nodes service)))))]
    (case ip-type
      :private (private-ip  master-node)
      :public (primary-ip  master-node))))

(defn jobtracker-ip
  "Returns a string containing the IP address of the jobtracker node
  instantiated in the service."
  [ip-type service]
  (master-ip service :jobtracker ip-type))

(defn namenode-ip
  "Returns a string containing the IP address of the namenode node
  instantiated in the service, if there is one"
  [ip-type service]
  (master-ip service :namenode ip-type))

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
    (cluster-spec :private
                  {:jobtracker (node-group [:jobtracker :namenode])
                   :slaves     (slave-group 1)}
                  :base-machine-spec {:os-family :ubuntu
                                      :os-version-matches "10.10"
                                      :os-64-bit true}
                  :base-props {:mapred-site {:mapred.task.timeout 300000
                                             :mapred.reduce.tasks 3
                                             :mapred.tasktracker.map.tasks.maximum 3
                                             :mapred.tasktracker.reduce.tasks.maximum 3
                                             :mapred.child.java.opts "-Xms1024m"}}))
  
  (boot-cluster  example-cluster :compute ec2-service)
  (start-cluster example-cluster :compute ec2-service))
