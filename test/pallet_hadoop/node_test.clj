(ns pallet-hadoop.node-test
  (:use [pallet-hadoop.node] :reload)
  (:use [clojure.test]))

(def test-cluster
  (cluster-spec :private
                {:master (node-group [:jobtracker :namenode])
                 :slaves (slave-group 1)}
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

(deftest set-vals-test
  (is (= {:key1 0 :key2 0} (set-vals {:key1 "face" :key2 8} 0))))

(deftest expand-aliases-test
  (is (= [:datanode :tasktracker] (expand-aliases [:slavenode :datanode])))
  (is (= [:datanode :tasktracker :namenode] (expand-aliases [:slavenode :namenode])))
  (is (= [:datanode :namenode] (expand-aliases [:datanode :namenode]))))

(deftest master?-test
  (is (= false (master? [:cake])))
  (is (= true (master? [:jobtracker]))))

(deftest roles->tags-test
  (is (= [:master :master] (roles->tags [:jobtracker :namenode]
                                        (:nodedefs test-cluster)))))

(deftest slave-group-test
  (is (thrown? AssertionError (slave-group)))
  (are [opts result] (= result (apply slave-group opts))
       [3]
       {:node {:roles [:slavenode] :spec {} :props {}} :count 3}

       [1 :props {:mapred-site {:prop "val"}}]
       {:node {:roles [:slavenode]
               :spec {}
               :props {:mapred-site {:prop "val"}}}
        :count 1}))


