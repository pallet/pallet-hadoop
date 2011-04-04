(ns pallet-cascalog.environments
  (:use [pallet.resource :only (phase)]
        [pallet.resource.package :only (package-manager)]
        [pallet.compute.vmfest :only (parallel-create-nodes)])
  (:require [pallet.compute :as compute]
            [pallet.core :as core])
  (:import [java.net InetAddress]))

;; ### Local Environment

(def vm-service
  (compute/compute-service-from-config-file :virtualbox))

(def parallel-env {}
  #_{:algorithms
   {:lift-fn core/parallel-lift
    :vmfest {:create-nodes-fn parallel-create-nodes}
    :converge-fn core/parallel-adjust-node-counts}})

(def local-proxy (format "http://%s:3128"
                         (.getHostAddress
                          (InetAddress/getLocalHost))))

(def remote-env
  (let [default-image  {:image
                        {:os-family :ubuntu
                         :os-64-bit true}}]
    {:tags (zipmap [:hadoop :namenode :jobtracker :slavenode]
                   (repeat default-image))}))

(def local-node-specs
  (merge remote-env
         {:proxy local-proxy
          :phases {:bootstrap
                   (phase
                    (package-manager
                     :configure :proxy local-proxy))}}))

(def vm-env
  (merge local-node-specs parallel-env))

;; ### EC2 Environment

(def ec2-service
  (compute/compute-service-from-config-file :aws))

