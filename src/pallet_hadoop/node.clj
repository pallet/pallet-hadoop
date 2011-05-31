(ns pallet-hadoop.node
  (:use [pallet.crate.automated-admin-user :only (automated-admin-user)]
        [pallet.extensions :only (phase def-phase-fn)]
        [pallet.crate.java :only (java)]
        [pallet.core :only (make-node lift converge)]
        [clojure.set :only (union)])
  (:require [pallet.core :as core]
            [pallet.crate.hadoop :as h]))

;; ## Hadoop Cluster Configuration

;; ### Utilities
;;
;; Hadoop doesn't really have too many requirements for its nodes --
;; we do have to layer a few properties onto the base nodespec,
;; however. The ports are the most important element. If the user
;; supplies some default set of ports, we'd like to some way to take
;; the union of the required hadoop ports and the supplied ports from
;; the base node. The following function converts all supplied
;; sequences to sets, takes the union, and converts the results back
;; into a vector. Any type of collection is fine, for `xs`.

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

;; ### Defaults

(def
  ^{:doc "Map between hadoop aliases and the roles they stand
  for. `:slavenode` acts as an alias for nodes that function as both
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
  "Predicate that determines whether or not the given sequence of
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
  "Accepts a map of `tag, hadoop-node` pairs and a sequence of roles,
  and returns a sequence of the corresponding node tags. A
  postcondition is implemented to make sure that every role exists in
  the given node-def map."
  [role-seq node-defs]
  {:post [(= (count %)
             (count role-seq))]}
  (let [find-tag (fn [k]
                   (some (fn [[tag def]]
                           (when (some #{k} (get-in def [:node :roles]))
                             tag))
                         node-defs))]
    (remove nil? (map find-tag role-seq))))

(defn roles->phases
  "Converts a sequence of hadoop roles into a sequence of the unique
  phases required by a node trying to take on each of these roles."
  [roles]
  (->> roles (mapcat role->phase-map) distinct vec))

(defn hadoop-phases
  "Returns a map of all possible hadoop phases. IP-type specifies..."
  [{:keys [nodedefs ip-type]} properties]
  (let [[jt-tag nn-tag] (roles->tags [:jobtracker :namenode] nodedefs)
        configure (phase
                   (h/configure ip-type nn-tag jt-tag properties))]
    {:bootstrap automated-admin-user
     :configure (phase (java :jdk)
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
  "Generates a node spec for a hadoop node, merging together the given
  basenode with the required properties for the defined hadoop
  roles. (We assume at this point that all aliases have been expanded.)"
  [{:keys [spec roles]}]
  (let [ports (->> roles (mapcat hadoop-ports) distinct vec)]
    (merge-with merge-to-vec
                spec
                {:inbound-ports ports})))

(defn hadoop-server-spec
  "Returns a map of all hadoop phases -- we'll need to modify the
  name, here, and change this to compose with over server-specs."
  [cluster {:keys [props roles]}]
  (select-keys (hadoop-phases cluster props)
               (roles->phases roles)))

(defn merge-node
  "Merges a node into the given cluster's base specs."
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
  "Equivalent to `server-spec` in the new pallet."
  [cluster tag node]
  (let [node (merge-node cluster node)]
    (apply core/make-node
           tag
           (hadoop-machine-spec node)
           (apply concat (hadoop-server-spec cluster node)))))

(defn hadoop-node
  "Generates a map representation of a Hadoop node, employing sane defaults."
  [role-seq & [count & {:keys [roles spec props]}]]
  {:pre [(or count (master? role-seq))]}
  {:node {:roles (merge-to-vec role-seq (or roles []))
          :spec (or spec {})
          :props (or props {})}
   :count (or count 1)})

(def slave-node (partial hadoop-node [:slavenode]))

(defn cluster-spec [ip-type nodedefs & {:as options}]
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

(defn set-kill
  [node-map]
  (zipmap (keys node-map)
          (repeat 0)))

(defn cluster->node-set
  "Converts a cluster to `node-set` represention, for use in a call to
  `pallet.core/lift`."
  [cluster]
  (keys (cluster->node-map cluster)))

;; ### High Level Converge and Lift

(defn converge-cluster
  [cluster & options]
  (apply core/converge
         (cluster->node-map cluster)
         options))

(defn lift-cluster
  [cluster & options]
  (apply core/lift
         (cluster->node-set cluster)
         options))

(defn boot-cluster
  [cluster & options]
  (apply converge-cluster
         cluster
         :phase [:configure
                 :publish-ssh-key
                 :authorize-jobtracker]
         options))

(defn start-cluster
  [cluster & options]
  (apply lift-cluster
         cluster
         :phase [:start-namenode
                 :start-hdfs
                 :start-jobtracker
                 :start-mapred]
         options))

(defn kill-cluster
  [cluster & options]
  (apply core/converge
         (set-kill (cluster->node-map cluster))
         options))
