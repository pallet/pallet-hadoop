(ns pallet-hadoop.node-test
  (:use [pallet-hadoop.node] :reload)
  (:use [clojure.test]))

(def test-cluster
  (cluster-spec :private
                {:jobtracker (hadoop-node [:jobtracker :namenode])
                 :slaves (slave-node 1)}
                :base-machine-spec {:os-family :ubuntu
                                    :os-version-matches "10.10"
                                    :os-64-bit true}
                :base-props {:mapred-site {:mapred.task.timeout 300000
                                           :mapred.reduce.tasks 50
                                           :mapred.tasktracker.map.tasks.maximum 15
                                           :mapred.tasktracker.reduce.tasks.maximum 15}}))

(deftest merge-to-vec-test
  (are [in-seqs out-vec] (let [out (apply merge-to-vec in-seqs)]
                           (and (vector? out)
                                (= (set out-vec)
                                   (set out))))
       [[1 2] [5 4] [2]] [1 2 4 5]
       [[1 2] [4 5]] [1 2 4 5]
       [["face" 2] [2 1]] [1 2 "face"]))

(deftest slave-node-test
  (is (thrown? AssertionError (slave-node)))
  (are [opts result] (= result (apply slave-node opts))
       [3]
       {:node {:roles [:slavenode]} :count 3}

       [1 :props {:mapred-site {:prop "val"}}]
       {:node {:roles [:slavenode]
               :base-spec {}
               :props {:mapred-site {:prop "val"}}}
        :count 3}))
