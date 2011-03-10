(ns pallet-cascalog.node
  (:use [pallet.core :only (defnode)]
        [pallet.resource :only (phase)]
        [pallet.crate.automated-admin-user
         :only (automated-admin-user)])
  (:require pallet.compute.vmfest
            [pallet.crate.hadoop :as hadoop]
            [pallet.crate.java :as java]))

(def parallel-env {}
  #_{:algorithms
   {:lift-fn pallet.core/parallel-lift
    :vmfest {:create-nodes-fn pallet.compute.vmfest/parallel-create-nodes}
    :converge-fn pallet.core/parallel-adjust-node-counts}})

(def local-proxy "http://10.0.1.9:3128")

(def local-node-specs
  {:tags
   {:hadoop
    {:image
     {:os-family :ubuntu
      :os-64-bit true}}}
   :phases {:bootstrap
            (fn [request]
              (pallet.resource.package/package-manager
               request
               :configure :proxy local-proxy))}
   :proxy local-proxy})

(def local-env
  (merge local-node-specs parallel-env))

(defnode hadoop
  {}
  :bootstrap automated-admin-user
  :configure (phase
              (java/java :jdk)
              hadoop/install
              (hadoop/configure "/tmp/hadoop/"
                                :name-node ;; name of the node
                                "job-tracker-name"
                                {}))
  :reconfigure (phase
                (hadoop/configure "/tmp/hadoop"
                                  :name-node ;; name of the node
                                  "job-tracker-name"
                                  {})))

(def name-node
  (-> hadoop
      (assoc-in
       [:phases :start] (phase
                         (hadoop/name-node "/tmp/node-name/data" )))
      (assoc-in
       [:tag] :name-node)))

(comment
  (use 'pallet.core)
  (use 'pallet.compute)
  (def service (compute-service-from-config-file :virtualbox))
  (use 'pallet-cascalog.node)
  (converge {hadoop 1} :compute service :environment local-env)
  (converge {name-node 0} :compute service :environment local-env))