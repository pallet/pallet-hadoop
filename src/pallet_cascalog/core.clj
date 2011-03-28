(ns pallet-cascalog.core
  (:use [pallet.resource :only (phase)]
        [pallet.crate.automated-admin-user
         :only (automated-admin-user)])
  (:require [pallet.core :as core]
            [pallet.compute :as compute]
            [pallet.crate.hadoop :as h]
            [pallet.crate.java :as j]))

;; Okay, here's the good stuff. We're trying to get a system up and
;; running that can configure a persistent hadoop cluster.
;;
;; to act as the hadoop user;
;; sudo su -s /bin/bash hadoop
;; export JAVA_HOME=$(dirname $(dirname $(update-alternatives --list java)))
;;
;; Okay, some notes:
;;
;; With jclouds 9b, I'm getting all sorts of errors. In config, we
;; need to make sure we're using aws-ec2, not just ec2. Also,
;; cake-pallet adds pallet as a dependency, which forces jclouds
;; beta-8... doesn't work, if we're trying to play in 9b's world.
;;
;; Either I have to go straight back to 8b, with cake-pallet and no
;; dependencies excluded,
;;
;; ## Configuring Proxy
;; Compile squid from scratch,
;;
;; ./configure --enable-removal-policies="heap,lru"
;; Then give the guys my configuration file, from my macbook.
;; TODO -- figure out how to get the proper user permissions!
;;
;; run squid -z the first time. squid -N runs with no daemon mode
;;
;; http://www.deckle.co.za/squid-users-guide/Squid_Configuration_Basics
;; http://www.deckle.co.za/squid-users-guide/Starting_Squid
;;
;; ## Configuring VMFest!
;; TODO -- link over to Toni's instructions, on how to test this bad
;; boy.
;; https://gist.github.com/867526
